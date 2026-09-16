package com.botwap.domain.engine;

import com.botwap.domain.menu.InteractiveOption;
import com.botwap.domain.model.ConversationSelection;
import com.botwap.domain.model.ConversationState;

import java.util.List;

/**
 * Resultado de procesar una entrada en el motor conversacional.
 *
 * <ul>
 *   <li>{@code responseText}: texto que el bot debe responder; {@code null} cuando
 *       el motor decide permanecer en silencio (p. ej. estado terminal).</li>
 *   <li>{@code nextState}: estado al que transiciona la conversación.</li>
 *   <li>{@code selection}: selección registrada en este nivel (o {@code null} si no aplica).</li>
 *   <li>{@code options}: menú interactivo nativo presentado al usuario (botones o
 *       lista); vacío para respuestas de texto puro.</li>
 * </ul>
 */
public record EngineResult(
        String responseText,
        ConversationState nextState,
        ConversationSelection selection,
        List<InteractiveOption> options) {

    /** Respuesta de texto sin menú ni selección (p. ej. re-prompt de entrada inválida). */
    public static EngineResult textOnly(String responseText, ConversationState nextState) {
        return new EngineResult(responseText, nextState, null, List.of());
    }

    /** Respuesta que presenta un menú interactivo nativo de WhatsApp. */
    public static EngineResult menu(String responseText, ConversationState nextState,
                                    ConversationSelection selection, List<InteractiveOption> options) {
        return new EngineResult(responseText, nextState, selection,
                options == null ? List.of() : options);
    }

    /**
     * Resultado sin respuesta: el bot no envía nada (estado terminal o entrada
     * que no amerita mensaje). El flujo solo persistirá el mensaje entrante.
     */
    public static EngineResult silent(ConversationState nextState) {
        return new EngineResult(null, nextState, null, List.of());
    }

    /** Indica si hay algo que enviar al usuario (texto no vacío). */
    public boolean hasResponse() {
        return responseText != null && !responseText.isBlank();
    }

    /** Indica si la respuesta presenta un menú interactivo. */
    public boolean hasMenu() {
        return options != null && !options.isEmpty();
    }
}