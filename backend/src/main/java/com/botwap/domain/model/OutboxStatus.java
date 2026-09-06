package com.botwap.domain.model;

/**
 * Estados de la cola de salida (patrón Outbox).
 *
 * <p>Una fila de {@code outbox_message} representa una respuesta pendiente de
 * entregar a WhatsApp (docs/DATABASE_DESIGN.md § 2.4).</p>
 */
public enum OutboxStatus {

    /** Lista para intentar el envío (o en espera de backoff). */
    PENDING,

    /** Reclamada por el OutboxPoller; lease de envío activo. */
    SENDING,

    /** Entregada correctamente. */
    SENT,

    /** Agotó los reintentos; requiere auditoría/reenvío manual. */
    FAILED
}