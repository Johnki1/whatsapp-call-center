package com.botwap.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Respuesta pendiente de entregar a WhatsApp (patrón Outbox).
 *
 * <p>Se inserta en la MISMA transacción que procesa el mensaje entrante
 * (Fase A), lo que garantiza entrega confiable: procesar y encolar son
 * atómicos. El {@link OutboxStatus} y los campos de lease/intentos permiten
 * reintentos y recuperación tras un crash (tabla {@code outbox_message}).</p>
 */
public record OutboxMessage(
        UUID id,
        UUID messageId,
        UUID conversationId,
        String waId,
        String payload,
        OutboxStatus status,
        short attempts,
        Instant nextAttemptAt,
        Instant leaseExpiresAt,
        String lastError,
        Instant createdAt,
        Instant updatedAt,
        Instant sentAt) {

    public OutboxMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(waId, "waId");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** Crea una fila de outbox lista para el primer intento de envío. */
    public static OutboxMessage pendingFor(UUID messageId, UUID conversationId, String waId,
                                           String payloadJson, Instant nextAttemptAt) {
        Instant now = Instant.now();
        return new OutboxMessage(UUID.randomUUID(), messageId, conversationId, waId, payloadJson,
                OutboxStatus.PENDING, (short) 0, nextAttemptAt, null, null, now, now, null);
    }

    public OutboxMessage withStatus(OutboxStatus newStatus) {
        Instant now = Instant.now();
        Instant newSentAt = newStatus == OutboxStatus.SENT
                ? now
                : sentAt;
        return new OutboxMessage(id, messageId, conversationId, waId, payload, newStatus, attempts,
                nextAttemptAt, leaseExpiresAt, lastError, createdAt, now, newSentAt);
    }

    public OutboxMessage withAttempts(short newAttempts) {
        return new OutboxMessage(id, messageId, conversationId, waId, payload, status, newAttempts,
                nextAttemptAt, leaseExpiresAt, lastError, createdAt, updatedAt, sentAt);
    }

    public OutboxMessage withNextAttemptAt(Instant newNextAttemptAt) {
        return new OutboxMessage(id, messageId, conversationId, waId, payload, status, attempts,
                newNextAttemptAt, leaseExpiresAt, lastError, createdAt, updatedAt, sentAt);
    }

    public OutboxMessage withLeaseExpiresAt(Instant newLeaseExpiresAt) {
        return new OutboxMessage(id, messageId, conversationId, waId, payload, status, attempts,
                nextAttemptAt, newLeaseExpiresAt, lastError, createdAt, updatedAt, sentAt);
    }

    public OutboxMessage withLastError(String newLastError) {
        return new OutboxMessage(id, messageId, conversationId, waId, payload, status, attempts,
                nextAttemptAt, leaseExpiresAt, newLastError, createdAt, updatedAt, sentAt);
    }
}