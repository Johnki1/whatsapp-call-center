/**
 * OutboxPoller: scheduler reactivo interno (sin brokers externos) que reclama
 * filas de {@code outbox_message} con {@code FOR UPDATE SKIP LOCKED} y entrega
 * las respuestas a través de {@code WhatsAppClient} (docs/ARCHITECTURE.md § 7b).
 *
 * <p>Implementación completa en la Fase 5 (reintentos, backoff y lease).</p>
 */
package com.botwap.infrastructure.outbox;