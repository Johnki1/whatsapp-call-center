package com.botwap.domain.port;

import com.botwap.domain.model.WhatsAppSendResult;
import reactor.core.publisher.Mono;

/**
 * Puerto de envío de mensajes a WhatsApp.
 *
 * <p>El motor conversacional NO conoce el canal real: solo produce texto de
 * respuesta. Este puerto lo entrega. Adaptadores:
 * {@code MockWhatsAppClient} (desarrollo/tests) y {@code MetaWhatsAppClient}
 * (Graph API de Meta, Fase 7).</p>
 */
public interface WhatsAppClient {

    Mono<WhatsAppSendResult> sendMessage(String waId, String text);
}