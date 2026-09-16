package com.botwap.infrastructure.whatsapp;

import com.botwap.config.WhatsAppProperties;
import com.botwap.domain.model.WhatsAppSendResult;
import com.botwap.domain.port.WhatsAppClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adaptador real de la WhatsApp Cloud API de Meta (Graph API) — FASE 7A.
 *
 * <p>Envía mensajes de texto mediante
 * {@code POST {baseUrl}/{apiVersion}/{phoneNumberId}/messages} con
 * {@code Authorization: Bearer <accessToken>} y cuerpo JSON:</p>
 *
 * <pre>
 * { "messaging_product": "whatsapp", "to": "&lt;waId&gt;", "type": "text",
 *   "text": { "body": "&lt;texto&gt;" } }
 * </pre>
 *
 * <p>Es un adaptador puro de transporte, completamente reactivo (sin
 * {@code block()}): no conoce el motor conversacional ni el Outbox.</p>
 *
 * <p><strong>Reintentos:</strong> este adaptador NO implementa {@code retryWhen},
 * backoff ni ninguna política de reintentos. Esa responsabilidad es exclusiva del
 * {@code OutboxPoller} (Fase 6), que ya aplica backoff exponencial y lease.
 * Duplicar la política multiplicaría las llamadas a Meta.</p>
 *
 * <p><strong>Timeouts:</strong> el cliente Reactor Netty aplica timeout de
 * conexión y de respuesta, de modo que una petición a Meta nunca puede quedar
 * esperando indefinidamente.</p>
 */
@Component
@ConditionalOnProperty(prefix = "whatsapp.client", name = "mode", havingValue = "meta")
public class MetaWhatsAppClient implements WhatsAppClient {

    private static final Logger log = LoggerFactory.getLogger(MetaWhatsAppClient.class);

    private static final String MESSAGING_PRODUCT = "whatsapp";
    private static final String MESSAGE_TYPE_TEXT = "text";
    private static final String MESSAGE_TYPE_INTERACTIVE = "interactive";
    private static final String FIELD_MESSAGES = "messages";
    private static final String FIELD_ERROR = "error";
    /** Coincide con {@code outbox_message.last_error VARCHAR(255)}. */
    private static final int MAX_ERROR_LENGTH = 255;
    private static final long DEFAULT_TIMEOUT_MS = 10_000L;
    private static final long DEFAULT_CONNECT_TIMEOUT_MS = 5_000L;

    /** Para interpretar el payload semántico del Outbox (no confidencial). */
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiVersion;
    private final String phoneNumberId;
    private final WebClient webClient;

    public MetaWhatsAppClient(WhatsAppProperties properties, WebClient.Builder builder) {
        WhatsAppProperties.Api api = properties.api();
        String accessToken = requireConfigured(api.accessToken(), "WHATSAPP_ACCESS_TOKEN");
        this.phoneNumberId = requireConfigured(api.phoneNumberId(), "WHATSAPP_PHONE_NUMBER_ID");
        this.apiVersion = requireConfigured(api.apiVersion(), "WHATSAPP_API_VERSION");
        String baseUrl = requireConfigured(api.baseUrl(), "WHATSAPP_GRAPH_BASE_URL");
        long timeoutMs = api.timeoutMs() > 0 ? api.timeoutMs() : DEFAULT_TIMEOUT_MS;
        long connectTimeoutMs = api.connectTimeoutMs() > 0 ? api.connectTimeoutMs() : DEFAULT_CONNECT_TIMEOUT_MS;

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(connectTimeoutMs))
                .responseTimeout(Duration.ofMillis(timeoutMs));

        this.webClient = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

        // Solo se registra configuración NO sensible: el access token nunca se loguea.
        log.info("MetaWhatsAppClient habilitado: baseUrl={} apiVersion={} phoneNumberId={} timeoutMs={} connectTimeoutMs={}",
                baseUrl, this.apiVersion, this.phoneNumberId, timeoutMs, connectTimeoutMs);
    }

    /**
     * Valida configuración obligatoria al arrancar (fail-fast con
     * {@code whatsapp.client.mode=meta}).
     *
     * <p>El VALOR nunca se incluye en el mensaje: solo el nombre de la variable.</p>
     */
    private static String requireConfigured(String value, String variableName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(variableName + " is required when whatsapp.client.mode=meta");
        }
        return value;
    }

    /**
     * Envía el payload del Outbox a través de la Graph API de Meta.
     *
     * <p>Traduce el payload semántico al cuerpo de la API:
     * <ul>
     *   <li>{@code {"text": "…"}} → mensaje de texto.</li>
     *   <li>{@code {"text": "…", "interactive": {…}}} → mensaje interactivo
     *       nativo ({@code type: "interactive"} con botones o lista).</li>
     * </ul>
     *
     * <p>Completamente reactivo: no bloquea en ningún punto. Los errores de
     * validación y los errores HTTP se propagan como {@code Mono.error} para que
     * el {@code OutboxPoller} decida el reintento.</p>
     *
     * @param waId        número destino en formato Meta (solo dígitos, sin {@code +})
     * @param payloadJson payload semántico del Outbox (ver {@link #buildBody})
     * @return {@code Mono} con el {@code wamid} confirmado por Meta
     */
    @Override
    public Mono<WhatsAppSendResult> sendMessage(String waId, String payloadJson) {
        if (waId == null || waId.isBlank()) {
            return Mono.error(new IllegalArgumentException("waId is required to send a WhatsApp message"));
        }
        if (payloadJson == null || payloadJson.isBlank()) {
            return Mono.error(new IllegalArgumentException("payload is required to send a WhatsApp message"));
        }

        Map<String, Object> body = buildBody(waId, payloadJson);

        return webClient.post()
                .uri("/{apiVersion}/{phoneNumberId}/messages", apiVersion, phoneNumberId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchangeToMono(this::toResult)
                .doOnError(error -> log.warn("MetaWhatsAppClient: envío fallido a waId={} ({})",
                        waId, safeDescription(error)));
    }

    /**
     * Construye el cuerpo de la Graph API a partir del payload semántico.
     *
     * <p>Si el payload contiene el nodo {@code interactive}, el mensaje se envía
     * como {@code type=interactive} reenviando ese nodo íntegro (Meta valida
     * botones/lista); en caso contrario, como texto.</p>
     */
    private Map<String, Object> buildBody(String waId, String payloadJson) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", MESSAGING_PRODUCT);
        body.put("to", waId);

        JsonNode payload = parsePayload(payloadJson);
        JsonNode interactive = payload == null ? null : payload.path("interactive");
        if (interactive != null && interactive.isObject()) {
            body.put("type", MESSAGE_TYPE_INTERACTIVE);
            body.put("interactive", interactive);
            return body;
        }

        String text = payload == null ? "" : payload.path("text").asText("");
        body.put("type", MESSAGE_TYPE_TEXT);
        body.put("text", Map.of("body", text));
        return body;
    }

    /** Parsea el payload JSON del Outbox; nunca propaga errores de formato. */
    private JsonNode parsePayload(String payloadJson) {
        try {
            return objectMapper.readTree(payloadJson);
        } catch (JsonProcessingException e) {
            log.error("Payload del outbox no es JSON válido; se envía como texto vacío");
            return null;
        }
    }

    /**
     * Traduce la respuesta HTTP a resultado de dominio o error seguro.
     *
     * <p>Se usa {@code exchangeToMono} (en lugar de {@code retrieve()}) porque
     * permite leer el cuerpo de error para extraer {@code code/message/fbtrace_id}.</p>
     */
    private Mono<WhatsAppSendResult> toResult(ClientResponse response) {
        if (response.statusCode().is2xxSuccessful()) {
            return successResult(response);
        }
        return failureResult(response);
    }

    /**
     * Respuesta 2xx: la API devuelve
     * {@code {"messaging_product":"whatsapp","messages":[{"id":"wamid..."}]}}.
     *
     * <p>Un 2xx sin {@code messages[0].id} es un error controlado: NUNCA se
     * devuelve {@code Mono.empty()}, porque el {@code OutboxPoller} no marcaría
     * el mensaje como {@code SENT} y quedaría colgado en {@code SENDING}.</p>
     */
    private Mono<WhatsAppSendResult> successResult(ClientResponse response) {
        int status = response.statusCode().value();
        return response.bodyToMono(JsonNode.class)
                .flatMap(body -> extractWamid(body, status))
                .switchIfEmpty(emptyBodyError(status));
    }

    /** Extrae {@code messages[0].id}; si no existe, produce un error controlado. */
    private Mono<WhatsAppSendResult> extractWamid(JsonNode body, int status) {
        JsonNode messages = body.path(FIELD_MESSAGES);
        if (messages.isArray() && !messages.isEmpty()) {
            String wamid = messages.get(0).path("id").asText(null);
            if (wamid != null && !wamid.isBlank()) {
                return Mono.just(new WhatsAppSendResult(wamid));
            }
        }
        return Mono.error(new MetaDeliveryException(
                "Meta API error: status=" + status + ", reason=missing messages[0].id"));
    }

    private Mono<WhatsAppSendResult> emptyBodyError(int status) {
        return Mono.error(new MetaDeliveryException(
                "Meta API error: status=" + status + ", reason=empty response body"));
    }

    /**
     * Error HTTP (400/401/403/404/409/429/500/502/503/504...): lee el cuerpo
     * para extraer únicamente {@code error.code}, {@code error.message} y
     * {@code error.fbtrace_id}. Nunca incluye el cuerpo completo, cabeceras ni
     * credenciales.
     */
    private Mono<WhatsAppSendResult> failureResult(ClientResponse response) {
        int status = response.statusCode().value();
        return response.bodyToMono(JsonNode.class)
                .onErrorResume(ex -> Mono.empty())   // cuerpo vacío o no-JSON
                .map(body -> safeErrorMessage(status, body))
                .defaultIfEmpty(safeErrorMessage(status, null))
                .flatMap(this::deliveryError);
    }

    private Mono<WhatsAppSendResult> deliveryError(String message) {
        return Mono.error(new MetaDeliveryException(message));
    }

    /** Construye un mensaje de error seguro, útil y truncado (sin datos sensibles). */
    private static String safeErrorMessage(int status, JsonNode body) {
        StringBuilder message = new StringBuilder("Meta API error: status=").append(status);
        JsonNode error = body == null ? null : body.path(FIELD_ERROR);
        if (error != null && error.isObject()) {
            appendIfPresent(message, "code", error.path("code"));
            appendIfPresent(message, "message", error.path("message"));
            appendIfPresent(message, "fbtrace_id", error.path("fbtrace_id"));
        }
        return truncate(message.toString(), MAX_ERROR_LENGTH);
    }

    private static void appendIfPresent(StringBuilder target, String label, JsonNode node) {
        if (node != null && !node.isMissingNode() && !node.isNull()) {
            target.append(", ").append(label).append('=').append(node.asText());
        }
    }

    /** Trunca para no exceder {@code outbox_message.last_error VARCHAR(255)}. */
    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /** Descripción segura de un error para logs (sin token ni cabeceras). */
    private static String safeDescription(Throwable error) {
        String message = error.getMessage() == null ? "" : error.getMessage();
        return truncate(error.getClass().getSimpleName() + ": " + message, MAX_ERROR_LENGTH);
    }
}