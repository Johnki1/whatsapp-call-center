package com.botwap.application.service;

import reactor.core.publisher.Mono;

/**
 * Caso de uso de la Fase A (ingesta): deduplicación, transacción de
 * procesamiento y encolado de la respuesta en el Outbox.
 *
 * <p><strong>Esqueleto declarado en FASE 2.</strong> La orquestación completa
 * (paso a paso de docs/ARCHITECTURE.md § 7, incluidas las transacciones R2DBC
 * con {@code SELECT ... FOR UPDATE}) se implementa en FASE 4.</p>
 */
public final class InboundMessageOrchestrator {

    /**
     * Procesa un mensaje entrante de forma duradera.
     *
     * @param waId  número del usuario (identidad de la conversación)
     * @param wamid identificador único del mensaje en WhatsApp
     * @param text  contenido del mensaje
     * @return completa cuando el COMMIT de la Fase A finaliza
     */
    public Mono<Void> processInbound(String waId, String wamid, String text) {
        throw new UnsupportedOperationException(
                "TODO FASE 4: orquestación de la Fase A (dedupe, transacción, engine, outbox).");
    }
}