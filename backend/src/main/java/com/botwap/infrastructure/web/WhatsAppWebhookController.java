package com.botwap.infrastructure.web;

import com.botwap.application.service.InboundMessageOrchestrator;
import com.botwap.config.WhatsAppProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Endpoint de entrada de la WhatsApp Cloud API de Meta.
 *
 * <ul>
 *   <li>{@code GET /webhook/whatsapp}: verificacion inicial del webhook (challenge).</li>
 *   <li>{@code POST /webhook/whatsapp}: recepcion de eventos (mensajes del usuario).</li>
 * </ul>
 */
@RestController
@RequestMapping("/webhook/whatsapp")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private static final String SIGNATURE_HEADER = "X-Hub-Signature-256";
    private static final String FIELD_MESSAGES = "messages";
    private static final String MESSAGE_TYPE_TEXT = "text";

    private final WhatsAppProperties properties;
    private final WebhookSignatureVerifier signatureVerifier;
    private final InboundMessageOrchestrator orchestrator;
    private final ObjectMapper objectMapper;

    public WhatsAppWebhookController(WhatsAppProperties properties,
                                     WebhookSignatureVerifier signatureVerifier,
                                     InboundMessageOrchestrator orchestrator,
                                     ObjectMapper objectMapper) {
        this.properties = properties;
        this.signatureVerifier = signatureVerifier;
        this.orchestrator = orchestrator;
        this.objectMapper = objectMapper;
    }

    /**
     * Verificacion del webhook solicitada por Meta al configurarlo.
     *
     * @return 200 con el {@code hub.challenge} si el token coincide; 403 en caso contrario.
     */
    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<ResponseEntity<String>> verify(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String verifyToken,
            @RequestParam("hub.challenge") String challenge) {

        boolean valid = Objects.equals("subscribe", mode)
                && Objects.equals(verifyToken, properties.webhook().verifyToken());

        return Mono.just(valid
                ? ResponseEntity.ok(challenge)
                : ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    /**
     * Recepcion de eventos de Meta.
     *
     * <p>Flujo:
     * 1. Verifica la firma HMAC-SHA256 (400 si no coincide).
     * 2. Extrae wa_id / wamid / texto (ignora {@code statuses} y no-texto).
     * 3. Delega en {@link InboundMessageOrchestrator} y responde 200 tras el COMMIT.</p>
     *
     * @param payloadBytes body crudo del payload (necesario para validar la firma).
     * @param signature    header {@value SIGNATURE_HEADER} con la firma HMAC-SHA256.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Void>> receive(
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            @RequestBody byte[] payloadBytes) {

        String rawBody = new String(payloadBytes, StandardCharsets.UTF_8);
        log.info("Webhook recibido ({} bytes)", payloadBytes.length);

        return signatureVerifier.verify(signature, rawBody)
                .flatMap(valid -> {
                    if (!valid) {
                        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).<Void>build());
                    }
                    return extractInbound(rawBody)
                            .flatMap(extracted -> {
                                log.info("Procesando mensaje inbound: wa_id={}, wamid={}",
                                        extracted.waId(), extracted.wamid());
                                return orchestrator.processInbound(extracted.waId(), extracted.wamid(), extracted.text())
                                        .thenReturn(ResponseEntity.ok().<Void>build());
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                log.info("Webhook ack: no contiene mensajes de texto procesables");
                                return Mono.just(ResponseEntity.ok().<Void>build());
                            }));
                })
                .onErrorResume(e -> {
                    log.error("Error procesando webhook", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<Void>build());
                });
    }

    /**
     * Extrae el primer mensaje de texto del payload de WhatsApp.
     *
     * @return {@code Mono<ExtractedMessage>} con wa_id, wamid y texto, o vacío si
     *         el payload no contiene mensajes de texto (p. ej. solo {@code statuses}).
     */
    private Mono<ExtractedMessage> extractInbound(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode entries = root.path("entry");
            if (!entries.isArray() || entries.isEmpty()) {
                return Mono.empty();
            }

            JsonNode changes = entries.get(0).path("changes");
            if (!changes.isArray() || changes.isEmpty()) {
                return Mono.empty();
            }

            JsonNode value = changes.get(0).path("value");
            if (!FIELD_MESSAGES.equals(changes.get(0).path("field").asText())) {
                return Mono.empty();
            }

            JsonNode messages = value.path("messages");
            if (!messages.isArray() || messages.isEmpty()) {
                return Mono.empty();
            }

            JsonNode msg = messages.get(0);
            if (!MESSAGE_TYPE_TEXT.equals(msg.path("type").asText())) {
                return Mono.empty();
            }

            String waId = msg.path("from").asText(null);
            String wamid = msg.path("id").asText(null);
            String text = msg.path("text").path("body").asText(null);

            if (waId == null || wamid == null || text == null) {
                return Mono.empty();
            }

            return Mono.just(new ExtractedMessage(waId, wamid, text));
        } catch (JsonProcessingException e) {
            log.warn("No se pudo parsear el payload del webhook", e);
            return Mono.error(e);
        }
    }

    /** Resultado de la extraccion de un mensaje inbound del payload de WhatsApp. */
    private record ExtractedMessage(String waId, String wamid, String text) {
    }
}
