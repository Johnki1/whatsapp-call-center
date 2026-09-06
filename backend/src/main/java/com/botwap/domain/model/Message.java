package com.botwap.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Mensaje entrante (del usuario) o saliente (del bot).
 *
 * <p>Registra el intercambio completo para auditoría y reconstrucción de la
 * conversación (tabla {@code message}). El {@code waMessageId} es único y
 * permite deduplicar reintentos del webhook.</p>
 */
public record Message(
        UUID id,
        UUID conversationId,
        String waMessageId,
        MessageDirection direction,
        MessageStatus status,
        MessageType type,
        String content,
        Instant createdAt,
        Instant sentAt) {

    public Message {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /** Mensaje del usuario recibido por el webhook (estado inicial RECEIVED). */
    public static Message inbound(UUID conversationId, String waMessageId, String content) {
        return new Message(UUID.randomUUID(), conversationId, waMessageId, MessageDirection.INBOUND,
                MessageStatus.RECEIVED, MessageType.TEXT, content, Instant.now(), null);
    }

    /** Mensaje de respuesta del bot, pendiente de envío (Outbox). */
    public static Message outboundPending(UUID conversationId, String content) {
        return new Message(UUID.randomUUID(), conversationId, null, MessageDirection.OUTBOUND,
                MessageStatus.PENDING, MessageType.TEXT, content, Instant.now(), null);
    }

    public Message withStatus(MessageStatus newStatus) {
        Instant now = Instant.now();
        Instant newSentAt = newStatus == MessageStatus.SENT ? now : sentAt;
        return new Message(id, conversationId, waMessageId, direction, newStatus, type, content, createdAt, newSentAt);
    }

    public Message withWaMessageId(String newWaMessageId) {
        return new Message(id, conversationId, newWaMessageId, direction, status, type, content, createdAt, sentAt);
    }
}