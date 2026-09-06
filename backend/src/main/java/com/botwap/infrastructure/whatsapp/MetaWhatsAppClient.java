package com.botwap.infrastructure.whatsapp;

import com.botwap.config.WhatsAppProperties;
import com.botwap.domain.model.WhatsAppSendResult;
import com.botwap.domain.port.WhatsAppClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Adaptador real de la WhatsApp Cloud API de Meta (Graph API).
 *
 * <p><strong>Esqueleto declarado en FASE 2.</strong> La implementación del
 * envío ({@code POST .../{phone_number_id}/messages} con Bearer token,
 * WebClient reactivo, timeout y reintentos ante 429/5xx) se desbloquea en la
 * Fase 7, cuando el registro de Meta esté disponible.</p>
 */
@Component
@ConditionalOnProperty(prefix = "whatsapp.client", name = "mode", havingValue = "meta")
public class MetaWhatsAppClient implements WhatsAppClient {

    private final WhatsAppProperties properties;

    public MetaWhatsAppClient(WhatsAppProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<WhatsAppSendResult> sendMessage(String waId, String text) {
        // El motor conversacional nunca llama a este adaptador: solo el OutboxPoller (Fase 5).
        throw new UnsupportedOperationException(
                "TODO FASE 7: envío real a Meta Graph API (requiere registro de Meta y variables de entorno).");
    }
}