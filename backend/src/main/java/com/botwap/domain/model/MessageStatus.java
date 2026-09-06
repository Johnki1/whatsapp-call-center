package com.botwap.domain.model;

/**
 * Estados de un mensaje (docs/DATABASE_DESIGN.md § 3.1).
 *
 * <ul>
 *   <li>{@link #RECEIVED} / {@link #PROCESSED}: mensajes entrantes.</li>
 *   <li>{@link #PENDING} / {@link #SENT} / {@link #FAILED}: mensajes salientes.</li>
 * </ul>
 */
public enum MessageStatus {

    /** Entrante persistido y aún no procesado. */
    RECEIVED,

    /** Entrante procesado (su respuesta ya quedó encolada en el Outbox). */
    PROCESSED,

    /** Saliente generado, esperando ser enviado por el OutboxPoller. */
    PENDING,

    /** Saliente entregado (Meta devolvió wamid / Mock respondió ok). */
    SENT,

    /** Saliente que agotó los reintentos. */
    FAILED
}