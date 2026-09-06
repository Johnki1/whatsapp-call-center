package com.botwap;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;

/**
 * Estructura base de tests con WebTestClient sobre el endpoint del webhook
 * y el healthcheck de Actuator.
 *
 * <p>Los tests funcionales del bot (flujo de menús, dedupe, outbox, etc.)
 * corresponden a las Fases 4-6 conforme a docs/TESTING_STRATEGY.md.</p>
 */
class WebhookEndpointIntegrationTest extends BaseIntegrationTest {

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

    @Test
    void webhookPostAcknowledgesInboundEvent() {
        // Esqueleto Fase 2: el POST responde 200 (ack); el procesamiento real
        // (firma + orquestación de la Fase A) llega en la Fase 4.
        webTestClient.post()
                .uri("/webhook/whatsapp")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "object": "whatsapp_business_account",
                          "entry": []
                        }
                        """)
                .exchange()
                .expectStatus().isOk();
    }
}