package com.botwap.infrastructure.whatsapp;

import com.botwap.domain.model.WhatsAppSendResult;
import com.botwap.domain.port.WhatsAppClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Adaptador de simulación para entornos de desarrollo y tests.
 *
 * <p>Permite ejecutar el sistema completo SIN credenciales reales de Meta:
 * el flujo E2E (webhook → motor → outbox → poller → envío) es idéntico al de
 * producción; solo cambia el transporte.</p>
 */
@Component
@ConditionalOnProperty(prefix = "whatsapp.client", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockWhatsAppClient implements WhatsAppClient {

    private static final Logger log = LoggerFactory.getLogger(MockWhatsAppClient.class);

    @Override
    public Mono<WhatsAppSendResult> sendMessage(String waId, String text) {
        // TODO FASE 7: si se requiere, registrar en BD el envío simulado.
        return Mono.fromSupplier(() -> {
            log.info("[mock-whatsapp] envío simulado a {} (sin contenido en logs)", waId);
            return new WhatsAppSendResult("mock-wamid-" + UUID.randomUUID());
        });
    }
}