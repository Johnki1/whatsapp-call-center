package com.botwap.domain.engine;

import com.botwap.domain.menu.InteractiveOption;
import com.botwap.domain.model.ConversationSelection;
import com.botwap.domain.model.ConversationState;

import java.util.List;

/**
 * Interfaz de los handlers por estado (patron State).
 *
 * <p>Cada handler interpreta la entrada del usuario EN EL CONTEXTO DE SU ESTADO,
 * produce la respuesta de texto, el siguiente estado, la seleccion a registrar
 * y, si presenta un menú, sus opciones interactivas nativas de WhatsApp
 * (botones o lista). El ConversationEngine resuelve el handler actual desde un
 * registro y delega.</p>
 */
public interface StateHandler {

    /** Contexto de ejecucion del handler. */
    record Context(String input, ConversationState currentState, String userName,
                   List<ConversationSelection> selections) {
    }

    /** Resultado de la interpretacion: texto respuesta + siguiente estado + seleccion + menú. */
    record Outcome(String responseText, ConversationState nextState,
                   ConversationSelection selection, List<InteractiveOption> options) {

        /** Respuesta de texto sin menú ni selección. */
        public static Outcome textOnly(String responseText, ConversationState nextState) {
            return new Outcome(responseText, nextState, null, List.of());
        }

        /**
         * Respuesta que presenta un menú interactivo (botones o lista): el texto
         * es la prosa del mensaje y {@code options} viaja hacia el payload de
         * Meta con los ids estables de cada opción.
         */
        public static Outcome menu(String responseText, ConversationState nextState,
                                   ConversationSelection selection, List<InteractiveOption> options) {
            return new Outcome(responseText, nextState, selection,
                    options == null ? List.of() : options);
        }

        /**
         * Resultado SIN respuesta al usuario.
         *
         * <p>Se usa en estados terminales y en entradas que no deben generar
         * mensaje: el orquestador persiste el entrante pero no encola ningún
         * saliente (el bot permanece en silencio).</p>
         */
        public static Outcome silent(ConversationState nextState) {
            return new Outcome(null, nextState, null, List.of());
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