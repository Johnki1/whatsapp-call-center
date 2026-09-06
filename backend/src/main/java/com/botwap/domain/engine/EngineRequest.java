package com.botwap.domain.engine;

import com.botwap.domain.model.ConversationState;

import java.util.UUID;

/**
 * Petición al motor conversacional: la entrada del usuario interpretada en el
 * contexto del estado actual de su conversación.
 */
public record EngineRequest(
        UUID conversationId,
        String waId,
        String input,
        ConversationState currentState) {
}