package com.botwap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;

class WebhookEndpointIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DatabaseClient databaseClient;

    private static final String WEBHOOK_SECRET = "test-app-secret";

    @BeforeEach
    void cleanDatabase() {
        databaseClient.sql("""
                        TRUNCATE TABLE outbox_message, message, conversation_selection, conversation
                        RESTART IDENTITY CASCADE
                        """)
                .then()
                .block();
    }

    private static String signPayload(String appSecret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static final String WHATSAPP_WEBHOOK_PAYLOAD = """
            {
              "object": "whatsapp_business_account",
              "entry": [{
                "id": "WABA_ID",
                "changes": [{
                  "field": "messages",
                  "value": {
                    "messaging_product": "whatsapp",
                    "messages": [{
                      "from": "573001111111",
                      "id": "wamid.test-123",
                      "type": "text",
                      "text": {"body": "hola"}
                    }]
                  }
                }]
              }]
            }
            """;

    private Mono<Long> countMessagesWithWamid(String wamid) {
        return databaseClient.sql(
                        "SELECT COUNT(*) AS cnt FROM message WHERE wa_message_id = :wamid")
                .bind("wamid", wamid)
                .fetch()
                .first()
                .map(row -> (Number) row.get("cnt"))
                .map(Number::longValue);
    }

    private Mono<Long> countOutboxRows() {
        return databaseClient.sql("SELECT COUNT(*) AS cnt FROM outbox_message")
                .fetch()
                .first()
                .map(row -> (Number) row.get("cnt"))
                .map(Number::longValue);
    }

    // ---- GET verification ----

    @Test
    void healthEndpointIsUp() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(containsString("\"UP\""));
    }

    @Test
    void webhookVerifyReturnsChallengeWhenTokenMatches() {
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/webhook/whatsapp")
                        .queryParam("hub.mode", "subscribe")
                        .queryParam("hub.verify_token", "test-verify-token")
                        .queryParam("hub.challenge", "challenge-value-123")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("challenge-value-123");
    }

    @Test
    void webhookVerifyRejectsInvalidToken() {
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/webhook/whatsapp")
                        .queryParam("hub.mode", "subscribe")
                        .queryParam("hub.verify_token", "wrong-token")
                        .queryParam("hub.challenge", "challenge-value-123")
                        .build())
                .exchange()
                .expectStatus().isForbidden();
    }

    // ---- POST ----

    @Test
    void webhookPostAcknowledgesEmptyEntry() {
        String payload = """
                {
                  "object": "whatsapp_business_account",
                  "entry": []
                }
                """;
        String signature = signPayload(WEBHOOK_SECRET, payload);

        webTestClient.post()
                .uri("/webhook/whatsapp")
                .header("X-Hub-Signature-256", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void webhookPostRejectsInvalidSignature() {
        String payload = """
                {
                  "object": "whatsapp_business_account",
                  "entry": []
                }
                """;

        webTestClient.post()
                .uri("/webhook/whatsapp")
                .header("X-Hub-Signature-256", "sha256=deadbeef")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void webhookPostProcessesInboundMessage() {
        String signature = signPayload(WEBHOOK_SECRET, WHATSAPP_WEBHOOK_PAYLOAD);

        webTestClient.post()
                .uri("/webhook/whatsapp")
                .header("X-Hub-Signature-256", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(WHATSAPP_WEBHOOK_PAYLOAD)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .isEmpty();

        // Verificar que el mensaje inbound fue persistido (no duplicado).
        StepVerifier.create(countMessagesWithWamid("wamid.test-123"))
                .assertNext(count -> assertThat(count).isEqualTo(1L))
                .verifyComplete();

        // Verificar que se creo una fila de outbox (respuesta encolada).
        StepVerifier.create(countOutboxRows())
                .assertNext(count -> assertThat(count).isEqualTo(1L))
                .verifyComplete();
    }

    @Test
    void webhookPostIsIdempotentOnDuplicateWamid() {
        String signature = signPayload(WEBHOOK_SECRET, WHATSAPP_WEBHOOK_PAYLOAD);

        // Primer envio.
        webTestClient.post()
                .uri("/webhook/whatsapp")
                .header("X-Hub-Signature-256", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(WHATSAPP_WEBHOOK_PAYLOAD)
                .exchange()
                .expectStatus().isOk();

        // Segundo envio (mismo wamid): debe ser ack pero no crear duplicados.
        webTestClient.post()
                .uri("/webhook/whatsapp")
                .header("X-Hub-Signature-256", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(WHATSAPP_WEBHOOK_PAYLOAD)
                .exchange()
                .expectStatus().isOk();

        // Solo debe haber 1 mensaje inbound para el wamid.
        StepVerifier.create(countMessagesWithWamid("wamid.test-123"))
                .assertNext(count -> assertThat(count).isEqualTo(1L))
                .verifyComplete();
    }
}
