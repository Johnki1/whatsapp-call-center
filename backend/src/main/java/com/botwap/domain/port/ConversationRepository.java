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

    Mono<Conversation> save(Conversation conversation);
}