package com.botwap.domain.engine;

import com.botwap.domain.model.ConversationState;
import com.botwap.domain.model.ConversationSelection;

import java.util.List;
import java.util.UUID;

/**
 * Peticion al motor conversacional: la entrada del usuario interpretada en el
 * contexto del estado actual de su conversacion.
 *
 * @param selections selecciones previamente persistadas en esta conversacion
 *                   (permite a los handlers validar contra el contexto real).
 */
public record EngineRequest(
        UUID conversationId,
        String waId,
        String input,
        ConversationState currentState,
        List<ConversationSelection> selections) {
}