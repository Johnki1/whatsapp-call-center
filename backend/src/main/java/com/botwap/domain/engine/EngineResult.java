package com.botwap.domain.engine;

import com.botwap.domain.model.ConversationSelection;
import com.botwap.domain.model.ConversationState;

/**
 * Resultado de procesar una entrada en el motor conversacional.
 *
 * <ul>
 *   <li>{@code responseText}: texto que el bot debe responder (puede estar vacío si no hay respuesta).</li>
 *   <li>{@code nextState}: estado al que transiciona la conversación.</li>
 *   <li>{@code selection}: selección registrada en este nivel (o {@code null} si no aplica).</li>
 * </ul>
 */
public record EngineResult(
        String responseText,
        ConversationState nextState,
        ConversationSelection selection) {

    /** Respuesta sin registro de selección (p. ej. re-prompt de entrada inválida). */
    public static EngineResult textOnly(String responseText, ConversationState nextState) {
        return new EngineResult(responseText, nextState, null);
    }
}