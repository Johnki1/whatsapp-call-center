package com.botwap.infrastructure.whatsapp;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.botwap.config.WhatsAppProperties;
import com.botwap.domain.model.WhatsAppSendResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de {@link MetaWhatsAppClient} (FASE 7A).
 *
 * <p><strong>Ningun test llama a Meta</strong>: se levanta un stub HTTP local
 * ({@link StubMetaGraphServer}) sobre Reactor Netty, ya presente en el classpath
 * via {@code spring-boot-starter-webflux}. Sin Internet, sin credenciales reales,
 * sin WireMock/MockWebServer y sin dependencias Maven nuevas.</p>
 */
class MetaWhatsAppClientTest {

    private static final String TEST_TOKEN = "test-access-token-not-real";
    private static final String APP_SECRET = "test-app-secret";
    private static final String PHONE_NUMBER_ID = "123456789012345";
    private static final String API_VERSION = "v21.0";
    private static final String WABA_ID = "test-waba";
    private static final String WA_ID = "573001112222";
    private static final String TEXT = "hola desde el test";
    private static final String TEXT_PAYLOAD = "{\"text\":\"hola desde el test\"}";
    private static final String WAMID = "wamid.test-123";

    private static final String EXPECTED_URI = "/v21.0/123456789012345/messages";

    private static final String JSON_WITHOUT_MESSAGES = """
            {"messaging_product":"whatsapp"}""";
    private static final String JSON_EMPTY_MESSAGES = """
            {"messages":[]}""";
    private static final String HTML_ERROR_BODY = "<html>service unavailable</html>";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private StubMetaGraphServer server;

    @BeforeEach
    void startStubServer() {
        server = new StubMetaGraphServer();
    }

    @AfterEach
    void stopStubServer() {
        server.close();
    }

    @Test
    void callsMessagesEndpointWithApiVersionAndPhoneNumberId() {
        server.respond(200, okBody(WAMID));

        StepVerifier.create(client().sendMessage(WA_ID, TEXT_PAYLOAD))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(server.requestCount()).isEqualTo(1);
        assertThat(server.lastRequest().method()).isEqualTo("POST");
        assertThat(server.lastRequest().uri()).isEqualTo(EXPECTED_URI);
    }

    @Test
    void sendsAccessTokenAsBearerAuthorizationHeader() {
        server.respond(200, okBody(WAMID));

        StepVerifier.create(client().sendMessage(WA_ID, TEXT_PAYLOAD))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(server.lastRequest().header("Authorization")).isEqualTo("Bearer " + TEST_TOKEN);
        assertThat(server.lastRequest().header("Content-Type")).contains("application/json");
    }

    @Test
    void sendsExactTextMessagePayload() throws Exception {
        server.respond(200, okBody(WAMID));

        StepVerifier.create(client().sendMessage(WA_ID, TEXT_PAYLOAD))
                .expectNextCount(1)
                .verifyComplete();

        JsonNode body = MAPPER.readTree(server.lastRequest().body());
        assertThat(body.size()).isEqualTo(4);
        assertThat(body.path("messaging_product").asText()).isEqualTo("whatsapp");
        assertThat(body.path("to").asText()).isEqualTo(WA_ID);
        assertThat(body.path("type").asText()).isEqualTo("text");
        assertThat(body.path("text").size()).isEqualTo(1);
        assertThat(body.path("text").path("body").asText()).isEqualTo(TEXT);
    }

    @Test
    void returnsWamidFromSuccessfulResponse() {
        server.respond(200, okBody(WAMID));

        StepVerifier.create(client().sendMessage(WA_ID, TEXT_PAYLOAD))
                .assertNext(result -> assertThat(result).isEqualTo(new WhatsAppSendResult(WAMID)))
                .verifyComplete();
    }

    @Test
    void failsWhenSuccessResponseHasNoMessagesArray() {
        server.respond(200, JSON_WITHOUT_MESSAGES);

        StepVerifier.create(client().sendMessage(WA_ID, TEXT_PAYLOAD))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(MetaDeliveryException.class);
                    assertThat(error.getMessage()).contains("status=200").contains("messages");
                })
                .verify();
    }

    @Test
    void failsWhenSuccessResponseHasEmptyMessagesArray() {
        server.respond(200, JSON_EMPTY_MESSAGES);

        StepVerifier.create(client().sendMessage(WA_ID, TEXT_PAYLOAD))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(MetaDeliveryException.class);
                    assertThat(error.getMessage()).contains("status=200");
                })
                .verify();
    }

    @Test
    void failsOnBadRequest400() {
        server.respond(400, errorBody(131026, "Message undeliverable."));

        StepVerifier.create(client().sendMessage(WA_ID, TEXT_PAYLOAD))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(MetaDeliveryException.class);
                    assertThat(error.getMessage())
                            .contains("Meta API error: status=400")
                            .contains("code=131026")
                            .contains("message=Message undeliverable.")
                            .contains("fbtrace_id=AbCdEfTrace");
                })
                .verify();
    }

    @Test
    void failsOnUnauthorized401() {
        server.respond(401, errorBody(190, "Invalid OAuth access token."));

        StepVerifier.create(client().sendMessage(WA_ID, TEXT_PAYLOAD))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(MetaDeliveryException.class);
                    assertThat(error.getMessage()).contains("status=401").contains("code=190");
                })
                .verify();
    }

    @Test
    void failsOnTooManyRequests429() {
        server.respond(429, errorBody(130429, "Rate limit hit."));

        StepVerifier.create(client().sendMessage(WA_ID, TEXT_PAYLOAD))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(MetaDeliveryException.class);
                    assertThat(error.getMessage()).contains("status=429").contains("code=130429");
                })
                .verify();
    }

    @Test
    void failsOnInternalServerError500() {
        server.respond(500, errorBody(131000, "Something went wrong."));

        StepVerifier.create(client().sendMessage(WA_ID, TEXT_PAYLOAD))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(MetaDeliveryException.class);
                    assertThat(error.getMessage()).contains("status=500").contains("code=131000");
                })
                .verify();
    }

    @Test
    void failsWithStatusOnlyWhenErrorBodyIsNotJson() {
        server.respond(503, HTML_ERROR_BODY);

        StepVerifier.create(client().sendMessage(WA_ID, TEXT_PAYLOAD))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(MetaDeliveryException.class);
                    assertThat(error.getMessage()).contains("status=503");
                    assertThat(error.getMessage()).doesNotContain("html");
                })
                .verify();
    }

    @Test
    void failsWithStatusOnlyWhenErrorBodyIsEmpty() {
        server.respond(502, "");

        StepVerifier.create(client().sendMessage(WA_ID, TEXT_PAYLOAD))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(MetaDeliveryException.class);
                    assertThat(error.getMessage()).contains("status=502");
                })
                .verify();
    }

    @Test
    void failsWhenResponseExceedsConfiguredTimeout() {
        server.respondAfter(200, okBody("wamid.late"), Duration.ofSeconds(3));

        StepVerifier.create(clientWithTimeouts(300L, 200L).sendMessage(WA_ID, TEXT_PAYLOAD))
                .expectError()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void rejectsBlankOrNullWaIdWithoutHttpCall() {
        MetaWhatsAppClient client = client();

        StepVerifier.create(client.sendMessage("   ", TEXT))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(IllegalArgumentException.class))
                .verify();
        StepVerifier.create(client.sendMessage(null, TEXT))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(IllegalArgumentException.class))
                .verify();

        assertThat(server.requestCount()).isZero();
    }

    @Test
    void rejectsBlankOrNullTextWithoutHttpCall() {
        MetaWhatsAppClient client = client();

        StepVerifier.create(client.sendMessage(WA_ID, "   "))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(IllegalArgumentException.class))
                .verify();
        StepVerifier.create(client.sendMessage(WA_ID, null))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(IllegalArgumentException.class))
                .verify();

        assertThat(server.requestCount()).isZero();
    }

    @Test
    void failsFastWhenAccessTokenIsBlank() {
        WhatsAppProperties props = properties(server.baseUrl(), "   ", PHONE_NUMBER_ID, API_VERSION, 1_000L, 500L);

        assertThatThrownBy(() -> new MetaWhatsAppClient(props, WebClient.builder()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WHATSAPP_ACCESS_TOKEN")
                .hasMessageContaining("whatsapp.client.mode=meta");
    }

    @Test
    void failsFastWhenAccessTokenIsNull() {
        WhatsAppProperties props = properties(server.baseUrl(), null, PHONE_NUMBER_ID, API_VERSION, 1_000L, 500L);

        assertThatThrownBy(() -> new MetaWhatsAppClient(props, WebClient.builder()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WHATSAPP_ACCESS_TOKEN");
    }

    @Test
    void failsFastWhenPhoneNumberIdIsMissing() {
        WhatsAppProperties props = properties(server.baseUrl(), TEST_TOKEN, "  ", API_VERSION, 1_000L, 500L);

        assertThatThrownBy(() -> new MetaWhatsAppClient(props, WebClient.builder()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WHATSAPP_PHONE_NUMBER_ID")
                .hasMessageNotContaining(TEST_TOKEN);
    }

    @Test
    void failsFastWhenApiVersionIsMissing() {
        WhatsAppProperties props = properties(server.baseUrl(), TEST_TOKEN, PHONE_NUMBER_ID, " ", 1_000L, 500L);

        assertThatThrownBy(() -> new MetaWhatsAppClient(props, WebClient.builder()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WHATSAPP_API_VERSION")
                .hasMessageNotContaining(TEST_TOKEN);
    }

    @Test
    void failsFastWhenBaseUrlIsMissing() {
        WhatsAppProperties props = properties(null, TEST_TOKEN, PHONE_NUMBER_ID, API_VERSION, 1_000L, 500L);

        assertThatThrownBy(() -> new MetaWhatsAppClient(props, WebClient.builder()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WHATSAPP_GRAPH_BASE_URL")
                .hasMessageNotContaining(TEST_TOKEN);
    }

    @Test
    void neverLeaksAccessTokenInErrorMessagesNorLogs() {
        server.respond(401, errorBody(190, "Invalid OAuth access token."));

        Logger logger = (Logger) LoggerFactory.getLogger(MetaWhatsAppClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            StepVerifier.create(client().sendMessage(WA_ID, TEXT_PAYLOAD))
                    .expectErrorSatisfies(error -> {
                        assertThat(error.getMessage()).doesNotContain(TEST_TOKEN);
                        assertThat(error.getMessage()).doesNotContain("Bearer");
                    })
                    .verify();

            List<String> logged = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();

            assertThat(logged).isNotEmpty();
            assertThat(logged).noneMatch(message -> message.contains(TEST_TOKEN));
            assertThat(logged).noneMatch(message -> message.contains("Authorization"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private MetaWhatsAppClient client() {
        return clientWithTimeouts(2_000L, 1_000L);
    }

    private MetaWhatsAppClient clientWithTimeouts(long timeoutMs, long connectTimeoutMs) {
        return new MetaWhatsAppClient(
                properties(server.baseUrl(), TEST_TOKEN, PHONE_NUMBER_ID, API_VERSION, timeoutMs, connectTimeoutMs),
                WebClient.builder());
    }

    private static WhatsAppProperties properties(String baseUrl, String accessToken, String phoneNumberId,
                                                 String apiVersion, long timeoutMs, long connectTimeoutMs) {
        return new WhatsAppProperties(
                new WhatsAppProperties.Client("meta"),
                new WhatsAppProperties.Webhook("test-verify-token"),
                new WhatsAppProperties.Api(APP_SECRET, accessToken, phoneNumberId, WABA_ID,
                        apiVersion, baseUrl, timeoutMs, connectTimeoutMs));
    }

    /** Respuesta 2xx con la forma real de la Graph API de Meta. */
    private static String okBody(String wamid) {
        return """
                {"messaging_product":"whatsapp","contacts":[{"input":"%s","wa_id":"%s"}],"messages":[{"id":"%s"}]}"""
                .formatted(WA_ID, WA_ID, wamid);
    }

    /** Respuesta de error con la forma real de la Graph API de Meta. */
    private static String errorBody(int code, String message) {
        return """
                {"error":{"message":"%s","type":"OAuthException","code":%d,"fbtrace_id":"AbCdEfTrace"}}"""
                .formatted(message, code);
    }
}
