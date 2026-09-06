package com.botwap.domain.model;

/**
 * Tipo de mensaje (coincide con el campo {@code type} del payload de Meta).
 *
 * <p>En la primera iteración el bot procesa y envía únicamente mensajes de texto.</p>
 */
public enum MessageType {

    /** Mensaje de texto plano. */
    TEXT,

    /** Interacción con botones/listas (fuera de alcance inicial). */
    INTERACTIVE
}