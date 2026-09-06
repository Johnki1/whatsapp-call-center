package com.botwap.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Conversación de un usuario: contexto de estado persistido.
 *
 * <p>Modelo de dominio inmutable; cada cambio produce una copia mediante
 * los métodos {@code with*}. Los adapters de persistencia (Fase 3) mapearán
 * este modelo a la tabla {@code conversation}.</p>
 */
public record Conversation(
        UUID id,
        String waId,
        ConversationState state,
        ConversationStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt) {

    public Conversation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(waId, "waId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** Crea una conversación nueva y activa en el menú principal. */
    public static Conversation newActive(String waId) {
        Instant now = Instant.now();
        return new Conversation(UUID.randomUUID(), waId, ConversationState.MAIN_MENU,
                ConversationStatus.ACTIVE, 0L, now, now, null);
    }

    public Conversation withState(ConversationState newState) {
        return new Conversation(id, waId, newState, status, version, createdAt, Instant.now(), closedAt);
    }

    public Conversation withStatus(ConversationStatus newStatus) {
        Instant now = Instant.now();
        return new Conversation(id, waId, state, newStatus, version, createdAt, now,
                newStatus == ConversationStatus.CLOSED ? now : closedAt);
    }

    public Conversation withVersion(long newVersion) {
        return new Conversation(id, waId, state, status, newVersion, createdAt, updatedAt, closedAt);
    }
}