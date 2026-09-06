package com.botwap.domain.port;

import com.botwap.domain.model.OutboxMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Puerto de la cola de salida (patrón Outbox).
 *
 * <p>Los métodos de reclamación/intentos se implementarán con
 * {@code FOR UPDATE SKIP LOCKED} dentro de una transacción (Fase 5).</p>
 */
public interface OutboxRepository {

    Mono<OutboxMessage> save(OutboxMessage outboxMessage);

    /** Fila pendiente de envío (reclamo exclusivo del OutboxPoller). */
    Flux<OutboxMessage> claimPending(int limit, boolean includeLeaseExpired);
}