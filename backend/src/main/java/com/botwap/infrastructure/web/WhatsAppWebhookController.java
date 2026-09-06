package com.botwap.infrastructure.web;

import com.botwap.config.WhatsAppProperties;
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

import java.util.Objects;

/**
 * Endpoint de entrada de la WhatsApp Cloud API de Meta.
 *
 * <ul>
 *   <li>{@code GET /webhook/whatsapp}: verificación inicial del webhook (challenge).</li>
 *   <li>{@code POST /webhook/whatsapp}: recepción de eventos (mensajes del usuario).</li>
 * </ul>
 *
 * <p>El POST es un <strong>esqueleto</strong>: el procesamiento real de la Fase A
 * (verificación de firma, extracción {@code wa_id}/{@code wamid}/texto y
 * orquestación) se implementa en la Fase 4 conforme a docs/ARCHITECTURE.md § 7.</p>
 */
@RestController
@RequestMapping("/webhook/whatsapp")
public class WhatsAppWebhookController {

    private static final String SIGNATURE_HEADER = "X-Hub-Signature-256";

    private final WhatsAppProperties properties;

    public WhatsAppWebhookController(WhatsAppProperties properties) {
        this.properties = properties;
    }

    /**
     * Verificación del webhook solicitada por Meta al configurarlo.
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
     * Recepción de eventos de Meta.
     *
     * @param rawBody   body crudo del payload (necesario para validar la firma en Fase 4)
     * @param signature header {@value SIGNATURE_HEADER} con la firma HMAC-SHA256
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Void>> receive(
            @RequestBody Mono<String> rawBody,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature) {

        // TODO FASE 4:
        //  1. Verificar firma con WebhookSignatureVerifier (400 si no coincide).
        //  2. Extraer wa_id / wamid / texto (ignorar statuses y no-texto).
        //  3. Delegar en InboundMessageOrchestrator y responder 200 tras el COMMIT.
        return rawBody.then(Mono.just(ResponseEntity.ok().<Void>build()));
    }
}