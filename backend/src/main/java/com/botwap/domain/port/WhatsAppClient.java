package com.botwap.domain.port;

import com.botwap.domain.model.WhatsAppSendResult;
import reactor.core.publisher.Mono;

/**
 * Puerto de envío de mensajes a WhatsApp.
 *
 * <p>El motor conversacional NO conoce el canal real: produce el contenido
 * (texto y, opcionalmente, menú interactivo) y el orquestador lo serializa al
 * payload del Outbox. Este puerto lo entrega. Adaptadores:
 * {@code MockWhatsAppClient} (desarrollo/tests) y {@code MetaWhatsAppClient}
 * (Graph API de Meta, Fase 7).</p>
 */
public interface WhatsAppClient {

    /**
     * Envía el payload semántico del Outbox.
     *
     * @param waId        número destino en formato Meta (solo dígitos, sin {@code +})
     * @param payloadJson JSON del outbox: {@code {"text": "…"}} para texto o
     *                    {@code {"text": "…", "interactive": {…}}} para la UI
     *                    nativa de Meta (botones/lista)
     * @return {@code Mono} con el {@code wamid} confirmado por Meta
     */
    Mono<WhatsAppSendResult> sendMessage(String waId, String payloadJson);
}