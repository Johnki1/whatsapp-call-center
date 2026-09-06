package com.botwap.domain.port;

import com.botwap.domain.model.ConversationSelection;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Puerto de persistencia de selecciones por nivel de la conversación.
 */
public interface ConversationSelectionRepository {

    Flux<ConversationSelection> findByConversationId(UUID conversationId);

    /** Alta o actualización (upsert por nivel) de la selección del nivel. */
    Mono<ConversationSelection> save(ConversationSelection selection);
}