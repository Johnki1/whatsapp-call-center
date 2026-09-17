package com.botwap.application.service;

import com.botwap.BaseIntegrationTest;
import com.botwap.config.OutboxProperties;
import com.botwap.domain.model.OutboxMessage;
import com.botwap.domain.model.OutboxStatus;
import com.botwap.domain.port.OutboxRepository;
import com.botwap.infrastructure.whatsapp.MockWhatsAppClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ContextConfiguration;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de integración del OutboxPoller (Fase 6).
 *
 * <p>Habilita el OutboxPoller para este test mediante
 * {@code @ContextConfiguration(initializers = ...)}.</p>
 */
@ContextConfiguration(initializers = OutboxPollerIntegrationTest.OutboxPollerInitializer.class)
class OutboxPollerIntegrationTest extends BaseIntegrationTest {

    /**
     * Inicializador que habilita el OutboxPoller para este test.
     */
    static class OutboxPollerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            TestPropertyValues.of(
                    "app.outbox.enabled=true",
                    "app.outbox.lease-duration-seconds=2",
                    "app.outbox.max-attempts=3",
                    "app.outbox.backoff-base-seconds=1"
            ).applyTo(context.getEnvironment());
        }
    }

    private static final String WA_ID = "573009998888";

    @Autowired
    OutboxRepository outboxRepository;

    @Autowired
    OutboxPoller outboxPoller;

    @Autowired
    MockWhatsAppClient mockWhatsAppClient;

    @Autowired
    DatabaseClient databaseClient;

    @Autowired
    OutboxProperties outboxProperties;

    @BeforeEach
    void setUp() {
        databaseClient.sql("""
                        TRUNCATE TABLE outbox_message, message, conversation_selection, conversation
                        RESTART IDENTITY CASCADE
                        """)
                .then()
                .block();
        mockWhatsAppClient.configureAlwaysSucceed();
    }

    @Test
    void pendingGoesToSentOnSuccess() {
        OutboxMessage outbox = createPendingOutbox();
        mockWhatsAppClient.configureAlwaysSucceed();
        StepVerifier.create(outboxPoller.processPending()).verifyComplete();
        StepVerifier.create(findOutboxStatus(outbox.id()))
                .assertNext(status -> assertThat(status).isEqualTo(OutboxStatus.SENT)).verifyComplete();
        StepVerifier.create(findSentAt(outbox.id()))
                .assertNext(sentAt -> assertThat(sentAt).isNotNull()).verifyComplete();
    }

    @Test
    void retryThenSucceeds() {
        OutboxMessage outbox = createPendingOutbox();
        // Falla la primera vez (intento #1), éxito la segunda (intento #2)
        mockWhatsAppClient.configureFailThenSucceed(1);
        // Primer intento: claim incrementa attempts 0→1, envío falla, scheduleRetry mantiene attempts=1
        StepVerifier.create(outboxPoller.processPending()).verifyComplete();
        StepVerifier.create(findOutboxStatus(outbox.id()))
                .assertNext(status -> assertThat(status).isEqualTo(OutboxStatus.PENDING)).verifyComplete();
        StepVerifier.create(findAttempts(outbox.id()))
                .assertNext(attempts -> assertThat(attempts).isEqualTo(1)).verifyComplete();
        // Avanzar tiempo para que sea elegible nuevamente
        databaseClient.sql("UPDATE outbox_message SET next_attempt_at = now() WHERE id = :id")
                .bind("id", outbox.id()).then().block();
        // Segundo intento: claim incrementa attempts 1→2, envío exitoso → SENT
        StepVerifier.create(outboxPoller.processPending()).verifyComplete();
        StepVerifier.create(findOutboxStatus(outbox.id()))
                .assertNext(status -> assertThat(status).isEqualTo(OutboxStatus.SENT)).verifyComplete();
        StepVerifier.create(findAttempts(outbox.id()))
                .assertNext(attempts -> assertThat(attempts).isEqualTo(2)).verifyComplete();
    }

    @Test
    void permanentFailureGoesToFailed() {
        OutboxMessage outbox = createPendingOutbox();
        mockWhatsAppClient.configureAlwaysFail();
        int maxAttempts = outboxProperties.maxAttempts();
        for (int i = 0; i < maxAttempts; i++) {
            databaseClient.sql("UPDATE outbox_message SET next_attempt_at = now() WHERE id = :id")
                    .bind("id", outbox.id()).then().block();
            StepVerifier.create(outboxPoller.processPending()).verifyComplete();
        }
        StepVerifier.create(findOutboxStatus(outbox.id()))
                .assertNext(status -> assertThat(status).isEqualTo(OutboxStatus.FAILED)).verifyComplete();
        StepVerifier.create(findAttempts(outbox.id()))
                .assertNext(attempts -> assertThat(attempts).isEqualTo(maxAttempts)).verifyComplete();
    }

    @Test
    void leasePreventsConcurrentClaim() {
        OutboxMessage outbox = createPendingOutbox();
        StepVerifier.create(outboxRepository.claimPending(10, false))
                .assertNext(claimed -> {
                    assertThat(claimed.status()).isEqualTo(OutboxStatus.SENDING);
                    assertThat(claimed.leaseExpiresAt()).isNotNull();
                }).verifyComplete();
        StepVerifier.create(outboxRepository.claimPending(10, false).count())
                .expectNext(0L).verifyComplete();
    }

    @Test
    void expiredLeaseAllowsRecovery() {
        OutboxMessage outbox = createPendingOutbox();
        StepVerifier.create(outboxRepository.claimPending(10, false))
                .assertNext(claimed -> assertThat(claimed.status()).isEqualTo(OutboxStatus.SENDING)).verifyComplete();
        databaseClient.sql("UPDATE outbox_message SET lease_expires_at = now() - interval '1 minute'")
                .then().block();
        StepVerifier.create(outboxRepository.claimPending(10, true))
                .assertNext(recovered -> {
                    assertThat(recovered.status()).isEqualTo(OutboxStatus.SENDING);
                    assertThat(recovered.id()).isEqualTo(outbox.id());
                }).verifyComplete();
    }

    @Test
    void sentMessageIsNotResent() {
        OutboxMessage outbox = createPendingOutbox();
        mockWhatsAppClient.configureAlwaysSucceed();
        StepVerifier.create(outboxPoller.processPending()).verifyComplete();
        StepVerifier.create(findOutboxStatus(outbox.id()))
                .assertNext(status -> assertThat(status).isEqualTo(OutboxStatus.SENT)).verifyComplete();
        StepVerifier.create(outboxPoller.processPending()).verifyComplete();
        StepVerifier.create(findOutboxStatus(outbox.id()))
                .assertNext(status -> assertThat(status).isEqualTo(OutboxStatus.SENT)).verifyComplete();
    }

    @Test
    void failedMessageIsNotRetried() {
        OutboxMessage outbox = createPendingOutbox();
        mockWhatsAppClient.configureAlwaysFail();
        int maxAttempts = outboxProperties.maxAttempts();
        for (int i = 0; i < maxAttempts; i++) {
            databaseClient.sql("UPDATE outbox_message SET next_attempt_at = now() WHERE id = :id")
                    .bind("id", outbox.id()).then().block();
            StepVerifier.create(outboxPoller.processPending()).verifyComplete();
        }
        StepVerifier.create(findOutboxStatus(outbox.id()))
                .assertNext(status -> assertThat(status).isEqualTo(OutboxStatus.FAILED)).verifyComplete();
        StepVerifier.create(outboxPoller.processPending()).verifyComplete();
        StepVerifier.create(findOutboxStatus(outbox.id()))
                .assertNext(status -> assertThat(status).isEqualTo(OutboxStatus.FAILED)).verifyComplete();
    }

    @Test
    void leaseRecoveryNoReenviaMasAllaDeMaxAttempts() {
        OutboxMessage outbox = createPendingOutbox();
        mockWhatsAppClient.configureAlwaysSucceed();

        // Simula un mensaje reclamado muchas veces sin confirmar (lease vencido):
        // ya superó maxAttempts, por lo que NO debe reenviarse nunca más.
        databaseClient.sql("""
                        UPDATE outbox_message
                        SET status = 'SENDING',
                            attempts = :attempts,
                            lease_expires_at = now() - interval '1 minute'
                        WHERE id = :id
                        """)
                .bind("attempts", outboxProperties.maxAttempts())
                .bind("id", outbox.id())
                .then().block();

        StepVerifier.create(outboxPoller.processPending()).verifyComplete();

        StepVerifier.create(findOutboxStatus(outbox.id()))
                .assertNext(status -> assertThat(status).isEqualTo(OutboxStatus.FAILED)).verifyComplete();
        assertThat(mockWhatsAppClient.getCallCount()).isZero();
    }

    @Test
    void multiplePendingMessagesAllSent() {
        for (int i = 0; i < 5; i++) {
            createPendingOutbox(WA_ID + "-" + i);
        }
        mockWhatsAppClient.configureAlwaysSucceed();
        StepVerifier.create(outboxPoller.processPending()).verifyComplete();
        StepVerifier.create(
                        databaseClient.sql("SELECT COUNT(*) AS cnt FROM outbox_message WHERE status = 'SENT'")
                                .map(row -> row.get("cnt", Number.class)).one())
                .assertNext(count -> assertThat(count.intValue()).isEqualTo(5)).verifyComplete();
    }

    @Test
    void backoffSetsNextAttemptAt() {
        OutboxMessage outbox = createPendingOutbox();
        mockWhatsAppClient.configureAlwaysFail();
        StepVerifier.create(outboxPoller.processPending()).verifyComplete();
        StepVerifier.create(
                        databaseClient.sql("SELECT next_attempt_at FROM outbox_message WHERE id = :id")
                                .bind("id", outbox.id())
                                .map(row -> row.get("next_attempt_at", Instant.class)).one())
                .assertNext(nextAttempt -> assertThat(nextAttempt).isAfter(Instant.now())).verifyComplete();
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private OutboxMessage createPendingOutbox() {
        return createPendingOutbox(WA_ID);
    }

    private OutboxMessage createPendingOutbox(String waId) {
        UUID conversationId = UUID.randomUUID();
        databaseClient.sql("""
                        INSERT INTO conversation (id, wa_id, state, status, version, created_at, updated_at, last_interaction_at)
                        VALUES (:id, :waId, 'MAIN_MENU', 'ACTIVE', 0, now(), now(), now())
                        """)
                .bind("id", conversationId)
                .bind("waId", waId)
                .then()
                .block();

        UUID messageId = UUID.randomUUID();
        databaseClient.sql("""
                        INSERT INTO message (id, conversation_id, wa_message_id, direction, status, type, content, created_at)
                        VALUES (:id, :convId, :wamid, 'OUTBOUND', 'PENDING', 'TEXT', 'test', now())
                        """)
                .bind("id", messageId)
                .bind("convId", conversationId)
                .bind("wamid", "wamid-" + messageId)
                .then()
                .block();

        OutboxMessage outbox = OutboxMessage.pendingFor(
                messageId, conversationId, waId, "{\"text\":\"mensaje de prueba\"}", Instant.now());

        return outboxRepository.save(outbox).block();
    }

    private reactor.core.publisher.Mono<OutboxStatus> findOutboxStatus(UUID id) {
        return databaseClient.sql("SELECT status FROM outbox_message WHERE id = :id")
                .bind("id", id)
                .map(row -> OutboxStatus.valueOf(row.get("status", String.class)))
                .one();
    }

    private reactor.core.publisher.Mono<Instant> findSentAt(UUID id) {
        return databaseClient.sql("SELECT sent_at FROM outbox_message WHERE id = :id")
                .bind("id", id)
                .map(row -> row.get("sent_at", Instant.class))
                .one();
    }

    private reactor.core.publisher.Mono<Integer> findAttempts(UUID id) {
        return databaseClient.sql("SELECT attempts FROM outbox_message WHERE id = :id")
                .bind("id", id)
                .map(row -> row.get("attempts", Number.class).intValue())
                .one();
    }
}
