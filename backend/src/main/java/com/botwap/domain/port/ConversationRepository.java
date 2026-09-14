package com.botwap.domain.port;

import com.botwap.domain.model.Conversation;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Puerto de persistencia de conversaciones.
 *
 * <p>Definido en el dominio; los adaptadores R2DBC (Fase 3) lo implementan.</p>
 */
public interface ConversationRepository {

    Mono<Conversation> findById(UUID id);

    /** Conversación activa del usuario (máximo una por {@code wa_id}). */
    Mono<Conversation> findActiveByWaId(String waId);

    /**
     * Conversación activa del usuario bloqueando la fila con
     * {@code SELECT ... FOR UPDATE} (serialización de mensajes simultáneos).
     *
     * <p><strong>Debe invocarse dentro de una transacción R2DBC real</strong>
     * (p. ej. mediante {@code TransactionalOperator}): fuera de transacción el
     * lock no se mantiene (docs/DATABASE_DESIGN.md § 3.3).</p>
     */
    Mono<Conversation> findActiveByWaIdForUpdate(String waId);

    /**
     * Inserta una conversación nueva.
     * @throws DomainException si ya existe una conversación activa para el mismo wa_id
     */
    Mono<Conversation> insert(Conversation conversation);

    /**
     * Actualiza una conversación existente con control de versión (optimistic lock).
     * @throws ConcurrencyConflictException si la versión esperada no coincide
     */
    Mono<Conversation> update(Conversation conversation);
}
