package com.botwap.domain.model;

/**
 * Ciclo de vida de una conversación.
 */
public enum ConversationStatus {

    /** Conversación en curso (una única activa por {@code wa_id}). */
    ACTIVE,

    /** Conversación finalizada (por respuesta final, cancelación o cierre). */
    CLOSED
}