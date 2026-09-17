package com.botwap.domain.port;

import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Puerto del asistente de IA (Gemini) para texto libre.
 *
 * <p>Cuando el usuario escribe texto libre en lugar de tocar un botón/lista
 * nativa, el orquestador consulta este puerto para clasificar la intención y
 * generar una respuesta amable, corta y profesional. Es un puerto de dominio:
 * la implementación real ({@code GeminiAiClient}) vive en infrastructure y es
 * fail-open — si la IA falla o está deshabilitada, el flujo conversacional
 * sigue funcionando con la máquina de estados pura.</p>
 */
public interface AiAssistant {

    /** Indica si el asistente está habilitado y configurado (API key presente). */
    boolean isEnabled();

    /**
     * Interpreta el texto libre del usuario en el contexto de la conversación.
     *
     * @param request contexto: nombre de perfil, estado actual, opciones del
     *                menú disponible y el texto del usuario
     * @return respuesta sugerida + intención clasificada
     */
    Mono<AiReply> assist(AiRequest request);

    /** Contexto enviado al modelo. */
    record AiRequest(String userName,
                     String conversationState,
                     List<String> menuOptions,
                     String userText) {
    }

    /**
     * Respuesta del modelo.
     *
     * @param reply  texto amable para el usuario (puede llegar vacío)
     * @param intent intención clasificada: {@code GREETING}, {@code PURCHASE},
     *               {@code RECHARGE}, {@code COMPLAINT}, {@code PERSONAL_INFO},
     *               {@code SUPPORT}, {@code AGENT} o {@code OTHER}; también se
     *               admite el ID de una opción del menú de categoría actual.
     *               El adaptador convierte confianza insuficiente en {@code OTHER}.
     *               La intención nunca autoriza operaciones ni omite identificación.
     */
    record AiReply(String reply, String intent) {

        public static AiReply empty() {
            return new AiReply("", "OTHER");
        }

        public boolean hasReply() {
            return reply != null && !reply.isBlank();
        }
    }
}
