package com.botwap.domain.port;

import com.botwap.domain.model.OutboxMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Puerto de la cola de salida (patrón Outbox).
 *
 * <p>Los métodos de reclamación/intentos se implementan con
 * {@code FOR UPDATE SKIP LOCKED} dentro de una transacción (Fase 5).</p>
 *
 * <p>Fase 6: se agregan métodos para marcar SENT, FAILED y programar
 * reintentos con backoff exponencial.</p>
 */
public interface OutboxRepository {

    Mono<OutboxMessage> save(OutboxMessage outboxMessage);

    /** Fila pendiente de envío (reclamo exclusivo del OutboxPoller). */
    Flux<OutboxMessage> claimPending(int limit, boolean includeLeaseExpired);

    /**
     * Marca un mensaje como enviado exitosamente.
     *
     * @param id     identificador del outbox
     * @param sentAt instante de envío confirmado
     * @return número de filas afectadas (1 si tuvo éxito)
     */
    Mono<Long> markSent(UUID id, Instant sentAt);

    /**
     * Marca un mensaje como fallido permanentemente (agotó reintentos).
     *
     * @param id    identificador del outbox
     * @param now   instante actual (para updated_at)
     * @param error mensaje de error resumido
     * @return número de filas afectadas
     */
    Mono<Long> markFailed(UUID id, Instant now, String error);

    /**
     * Programa un nuevo intento con backoff exponencial.
     *
     * <p>Transición SENDING → PENDING: incrementa attempts, calcula
     * next_attempt_at, limpia lease_expires_at y registra el error.</p>
     *
     * @param id             identificador del outbox
     * @param attempts       nuevo valor de attempts (incrementado)
     * @param nextAttemptAt  instante en que será elegible nuevamente
     * @param now            instante actual (para updated_at)
     * @param error          mensaje de error resumido
     * @return número de filas afectadas
     */
    Mono<Long> scheduleRetry(UUID id, int attempts, Instant nextAttemptAt, Instant now, String error);
}