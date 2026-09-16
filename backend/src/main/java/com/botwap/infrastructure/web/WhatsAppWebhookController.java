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
    private static final String MESSAGE_TYPE_INTERACTIVE = "interactive";

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
        log.info("[WEBHOOK RAW] Payload recibido: {} | X-Hub-Signature-256: {}", rawBody, signature);
        log.info("Webhook recibido ({} bytes)", payloadBytes.length);

        return signatureVerifier.verify(signature, rawBody)
                .flatMap(valid -> {
                    if (!valid) {
                        log.warn("[WEBHOOK] Firma HMAC rechazada. Se responde 400 sin procesar. X-Hub-Signature-256 recibida: {}",
                                signature);
                        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).<Void>build());
                    }
                    return extractInbound(rawBody)
                            .flatMap(extracted -> {
                                log.info("Procesando mensaje inbound: wa_id={}, wamid={}",
                                        extracted.waId(), extracted.wamid());
                                return orchestrator.processInbound(extracted.waId(), extracted.wamid(),
                                                extracted.text(), extracted.profileName())
                                        .thenReturn(ResponseEntity.ok().<Void>build());
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                log.info("Webhook ack: no contiene mensajes de texto procesables");
                                return Mono.just(ResponseEntity.ok().<Void>build());
                            }));
                })
                .onErrorResume(e -> {
                    // Contrato Meta: el webhook SIEMPRE responde 200 OK (salvo firma
                    // invalida, que es 400). Un 500 provoca reintentos agresivos de
                    // Meta -> tormenta de duplicados -> mas contencion en BD.
                    // El orquestador ya hizo rollback limpio; aqui solo se registra
                    // y se hace ack para cortar el ciclo de reintentos.
                    log.error("[WEBHOOK] Error no controlado procesando mensaje inbound ({}: {}). Rollback limpio, se responde 200 para evitar reintentos de Meta.",
                            e.getClass().getName(), e.getMessage(), e);
                    return Mono.just(ResponseEntity.ok().<Void>build());
                });
    }

    /**
     * Extrae el primer mensaje accionable del payload de WhatsApp.
     *
     * <p>Tipos soportados:
     * <ul>
     *   <li>{@code text}: el cuerpo del mensaje (texto libre).</li>
     *   <li>{@code interactive}: el id de la opción tocada
     *       ({@code button_reply.id} / {@code list_reply.id}), que coincide con el
     *       {@code optionKey} enviado en el payload interactivo del Outbox.</li>
     * </ul>
     * Además extrae el nombre público del perfil de WhatsApp
     * ({@code contacts[0].profile.name}) para personalizar el bot.
     *
     * @return {@code Mono<ExtractedMessage>} con wa_id, wamid, texto y nombre de
     *         perfil, o vacío si el payload no contiene mensajes accionables
     *         (p. ej. solo {@code statuses}).
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
            String text = extractText(msg);
            if (text == null) {
                return Mono.empty();
            }

            String waId = msg.path("from").asText(null);
            String wamid = msg.path("id").asText(null);
            String profileName = value.path("contacts").path(0)
                    .path("profile").path("name").asText(null);

            if (waId == null || wamid == null) {
                return Mono.empty();
            }

            return Mono.just(new ExtractedMessage(waId, wamid, text, profileName));
        } catch (JsonProcessingException e) {
            // Incluye el body completo: si Meta envia un campo inesperado, el stacktrace
            // + el payload permiten diagnosticar sin reproducir el request.
            log.error("[WEBHOOK] Deserializacion Jackson fallida para payload: {}", rawBody, e);
            return Mono.error(e);
        } catch (Exception e) {
            log.error("[WEBHOOK] Error inesperado extrayendo mensaje inbound del payload: {}", rawBody, e);
            return Mono.error(e);
        }
    }

    /** Texto accionable del mensaje: cuerpo de texto o id de la opción interactiva. */
    private String extractText(JsonNode msg) {
        String type = msg.path("type").asText();
        if (MESSAGE_TYPE_TEXT.equals(type)) {
            return msg.path("text").path("body").asText(null);
        }
        if (MESSAGE_TYPE_INTERACTIVE.equals(type)) {
            JsonNode interactive = msg.path("interactive");
            String id = interactive.path("button_reply").path("id").asText(null);
            return id != null ? id : interactive.path("list_reply").path("id").asText(null);
        }
        return null;
    }

    /** Resultado de la extraccion de un mensaje inbound del payload de WhatsApp. */
    private record ExtractedMessage(String waId, String wamid, String text, String profileName) {
    }
}
