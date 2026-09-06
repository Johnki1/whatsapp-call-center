package com.botwap.domain.model;

/**
 * Dirección de un mensaje con respecto al sistema.
 */
public enum MessageDirection {

    /** Mensaje recibido del usuario por el webhook de Meta. */
    INBOUND,

    /** Mensaje generado por el bot y enviado al usuario. */
    OUTBOUND
}