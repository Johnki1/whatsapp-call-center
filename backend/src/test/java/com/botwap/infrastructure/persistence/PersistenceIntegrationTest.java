package com.botwap.infrastructure.persistence;

import com.botwap.BaseIntegrationTest;
import com.botwap.application.exception.ConcurrencyConflictException;
import com.botwap.application.exception.DomainException;
import com.botwap.domain.model.Conversation;
import com.botwap.domain.model.ConversationSelection;
import com.botwap.domain.model.ConversationState;
import com.botwap.domain.model.Message;
import com.botwap.domain.model.MessageStatus;
import com.botwap.domain.model.OutboxMessage;
import com.botwap.domain.model.OutboxStatus;
import com.botwap.domain.port.ConversationRepository;
import com.botwap.domain.port.ConversationSelectionRepository;
import com.botwap.domain.port.MessageRepository;
import com.botwap.domain.port.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de la capa de persistencia R2DBC (FASE 3): conversación (índice único
 * parcial + optimistic lock + FOR UPDATE), upsert de selecciones, dedupe por
 * wamid, transición idempotente RECEIVED→PROCESSED y reclamo del Outbox.
 */
class PersistenceIntegrationTest extends BaseIntegrationTest {

    private static final String WA_A = "573001111111";

    @Autowired
    ConversationRepository conversationRepository;

    @Autowired
    ConversationSelectionRepository selectionRepository;

    @Autowired
    MessageRepository messageRepository;

    @Autowired
    OutboxRepository outboxRepository;

    @Autowired
    DatabaseClient databaseClient;

    @Autowired
    TransactionalOperator transactionalOperator;

    @BeforeEach
    void cleanDatabase() {
        databaseClient.sql("""
                        TRUNCATE TABLE outbox_message, message, conversation_selection, conversation
                        RESTART IDENTITY CASCADE
                        """)
                .then()
                .block();
    }

    // ---------------------------------------------------------------
    // conversation
    // ---------------------------------------------------------------

    @Test
    void savesAndFindsActiveConversation() {
        Conversation saved = conversationRepository.save(Conversation.newActive(WA_A)).block();

        assertThat(saved).isNotNull();
        assertThat(saved.version()).isZero();
        assertThat(saved.state()).isEqualTo(ConversationState.MAIN_MENU);

        StepVerifier.create(conversationRepository.findActiveByWaId(WA_A))
                .assertNext(found -> {
                    assertThat(found.id()).isEqualTo(saved.id());
                    assertThat(found.status().name()).isEqualTo("ACTIVE");
                })
                .verifyComplete();
    }

    @Test
    void rejectsSecondActiveConversationForSameWaId() {
        conversationRepository.save(Conversation.newActive(WA_A)).block();

        // El índice único parcial (wa_id) WHERE status='ACTIVE' impide dos activas.
        StepVerifier.create(conversationRepository.save(Conversation.newActive(WA_A)))
                .expectErrorMatches(e -> e instanceof DomainException)
                .verify();
    }

    @Test
    void optimisticLockDetectsConcurrentUpdate() {
        conversationRepository.save(Conversation.newActive(WA_A)).block();
        Conversation reloaded = conversationRepository.findActiveByWaId(WA_A).block();

        // Actualización correcta (versión esperada = 0).
        Conversation updated = conversationRepository
                .save(reloaded.withState(ConversationState.PURCHASE_MENU))
                .block();
        assertThat(updated.version()).isEqualTo(1L);

        // Actualización con versión obsoleta (0) sobre la fila ya versionada (1).
        StepVerifier.create(conversationRepository
                        .save(reloaded.withState(ConversationState.RECHARGE_MENU)))
                .expectErrorMatches(e -> e instanceof ConcurrencyConflictException)
                .verify();
    }

    // ---------------------------------------------------------------
    // conversation_selection (upsert por nivel)
    // ---------------------------------------------------------------

    @Test
    void selectionIsUpsertedByLevel() {
        Conversation conversation = conversationRepository.save(Conversation.newActive(WA_A)).block();

        selectionRepository.save(ConversationSelection.of(
                        conversation.id(), 1, "MAIN_MENU", "PURCHASE", "Compra de paquetes"))
                .block();
        selectionRepository.save(ConversationSelection.of(
                        conversation.id(), 2, "PURCHASE_MENU", "INTERNET_MOBILE", "Internet móvil"))
                .block();

        StepVerifier.create(selectionRepository.findByConversationId(conversation.id()).count())
                .expectNext(2L)
                .verifyComplete();

        // Upsert del nivel 1: se actualiza, no se duplica.
        selectionRepository.save(ConversationSelection.of(
                        conversation.id(), 1, "MAIN_MENU", "RECHARGE", "Recargas"))
                .block();

        StepVerifier.create(selectionRepository.findByConversationId(conversation.id()).count())
                .expectNext(2L)
                .verifyComplete();

        StepVerifier.create(selectionRepository.findByConversationId(conversation.id())
                        .filter(s -> s.level() == 1))
                .assertNext(s -> assertThat(s.optionKey()).isEqualTo("RECHARGE"))
                .verifyComplete();
    }

    // ---------------------------------------------------------------
    // message (dedupe por wamid + transición idempotente)
    // ---------------------------------------------------------------

    @Test
    void markReceivedAsProcessedIsIdempotent() {
        Conversation conversation = conversationRepository.save(Conversation.newActive(WA_A)).block();
        Message inbound = messageRepository
                .save(Message.inbound(conversation.id(), "wamid-abc", "hola"))
                .block();

        assertThat(inbound.status()).isEqualTo(MessageStatus.RECEIVED);

        StepVerifier.create(messageRepository.markReceivedAsProcessed(inbound.id()))
                .expectNext(true)
                .verifyComplete();

        // Segundo intento (reintento de Meta): ya procesado → false, sin efecto.
        StepVerifier.create(messageRepository.markReceivedAsProcessed(inbound.id()))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void duplicateWamidIsRejectedAndDetected() {
        Conversation conversation = conversationRepository.save(Conversation.newActive(WA_A)).block();

        messageRepository.save(Message.inbound(conversation.id(), "wamid-unico", "hola")).block();

        StepVerifier.create(messageRepository.existsByWaMessageId("wamid-unico"))
                .expectNext(true)
                .verifyComplete();

        // El índice único de wa_message_id impide duplicar el mismo mensaje.
        StepVerifier.create(messageRepository.save(
                        Message.inbound(conversation.id(), "wamid-unico", "hola de nuevo")))
                .expectErrorMatches(e -> e instanceof DataIntegrityViolationException)
                .verify();
    }

    // ---------------------------------------------------------------
    // outbox_message (reclamo SKIP LOCKED + recuperación por lease)
    // ---------------------------------------------------------------

    @Test
    void outboxClaimsPendingOnceAndRecoversExpiredLease() {
        Conversation conversation = conversationRepository.save(Conversation.newActive(WA_A)).block();
        Message outbound = messageRepository
                .save(Message.outboundPending(conversation.id(), "respuesta"))
                .block();

        outboxRepository.save(OutboxMessage.pendingFor(
                        outbound.id(), conversation.id(), WA_A, "{\"text\":\"respuesta\"}", Instant.now()))
                .block();

        // Primer reclamo: PENDING → SENDING (lease activo).
        StepVerifier.create(outboxRepository.claimPending(5, false))
                .assertNext(claimed -> {
                    assertThat(claimed.status()).isEqualTo(OutboxStatus.SENDING);
                    assertThat(claimed.attempts()).isZero();
                    assertThat(claimed.leaseExpiresAt()).isNotNull();
                })
                .verifyComplete();

        // Segundo reclamo (mismo tick): no hay filas PENDING que reclamar.
        StepVerifier.create(outboxRepository.claimPending(5, false).count())
                .expectNext(0L)
                .verifyComplete();

        // Las filas con next_attempt_at futuro aún no son elegibles.
        Message other = messageRepository
                .save(Message.outboundPending(conversation.id(), "otra")).block();
        outboxRepository.save(OutboxMessage.pendingFor(
                        other.id(), conversation.id(), WA_A, "{}", Instant.now().plusSeconds(300)))
                .block();
        StepVerifier.create(outboxRepository.claimPending(5, false).count())
                .expectNext(0L)
                .verifyComplete();

        // Crash a mitad de envío: lease vencido → la fila se recupera.
        databaseClient.sql("UPDATE outbox_message SET lease_expires_at = now() - interval '1 minute'")
                .then()
                .block();
        StepVerifier.create(outboxRepository.claimPending(5, true))
                .assertNext(recovered -> assertThat(recovered.status()).isEqualTo(OutboxStatus.SENDING))
                .verifyComplete();
    }

    // ---------------------------------------------------------------
    // SELECT ... FOR UPDATE dentro de transacción R2DBC real
    // ---------------------------------------------------------------

    @Test
    void forUpdateRunsInsideTransaction() {
        transactionalOperator.execute(tx ->
                        conversationRepository.save(Conversation.newActive(WA_A))
                                .then(conversationRepository.findActiveByWaIdForUpdate(WA_A))
                                .flatMap(locked -> conversationRepository
                                        .save(locked.withState(ConversationState.PURCHASE_MENU))))
                .then()
                .as(StepVerifier::create)
                .verifyComplete();

        StepVerifier.create(conversationRepository.findActiveByWaId(WA_A))
                .assertNext(found -> assertThat(found.state()).isEqualTo(ConversationState.PURCHASE_MENU))
                .verifyComplete();
    }
}