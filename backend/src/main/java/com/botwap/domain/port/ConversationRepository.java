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
     * Persiste la conversación: inserta si la fila no existe o actualiza con
     * control de versión (optimistic lock) si existe.
     */
    Mono<Conversation> save(Conversation conversation);
}