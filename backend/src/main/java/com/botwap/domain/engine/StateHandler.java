package com.botwap.domain.engine;

import com.botwap.domain.model.ConversationSelection;
import com.botwap.domain.model.ConversationState;

import java.util.List;

/**
 * Interfaz de los handlers por estado (patron State).
 *
 * <p>Cada handler interpreta la entrada del usuario EN EL CONTEXTO DE SU ESTADO,
 * produce la respuesta de texto, el siguiente estado y, de ser necesario,
 * la seleccion a registrar. El ConversationEngine resuelve el handler actual
 * desde un registro y delega.</p>
 */
public interface StateHandler {

    /** Contexto de ejecucion del handler. */
    record Context(String input, ConversationState currentState,
                   List<ConversationSelection> selections) {
    }

    /** Resultado de la interpretacion: texto respuesta + siguiente estado + seleccion opcional. */
    record Outcome(String responseText, ConversationState nextState,
                   ConversationSelection selection) {
        public static Outcome textOnly(String responseText, ConversationState nextState) {
            return new Outcome(responseText, nextState, null);
        }
    }

    /**
     * Procesa la entrada en el contexto de este estado.
     *
     * @param ctx informacion del contexto de conversacion.
     * @return resultado de la interpretacion.
     */
    Outcome handle(Context ctx);
}