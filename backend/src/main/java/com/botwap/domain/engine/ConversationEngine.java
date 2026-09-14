package com.botwap.domain.engine;

import com.botwap.domain.model.Conversation;

/**
 * Motor conversacional (máquina de estados).
 *
 * <p>Resuelve el {@code ConversationStateHandler} correspondiente al estado
 * actual de la conversación, valida la entrada y produce la transición
 * (respuesta, siguiente estado, selección).</p>
 */
public interface ConversationEngine {

    EngineResult process(EngineRequest request, Conversation conversation);
}