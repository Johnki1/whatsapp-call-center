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
        String profileName,
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
    public static Conversation newActive(String waId, String profileName) {
        Instant now = Instant.now();
        return new Conversation(UUID.randomUUID(), waId, ConversationState.MAIN_MENU,
                ConversationStatus.ACTIVE, normalizeProfileName(profileName), 0L, now, now, null);
    }

    /**
     * Normaliza el nombre de perfil para persistirlo: recorta extremos, colapsa
     * espacios y limita a {@value #MAX_PROFILE_NAME_LENGTH} caracteres (columna
     * {@code profile_name VARCHAR(100)}).
     */
    public static String normalizeProfileName(String profileName) {
        if (profileName == null) {
            return null;
        }
        String clean = profileName.trim().replaceAll("\\s+", " ");
        return clean.isEmpty() ? null
                : clean.substring(0, Math.min(clean.length(), MAX_PROFILE_NAME_LENGTH));
    }

    /** Longitud máxima del nombre de perfil almacenado. */
    public static final int MAX_PROFILE_NAME_LENGTH = 100;

    public Conversation withState(ConversationState newState) {
        return new Conversation(id, waId, newState, status, profileName, version, createdAt,
                Instant.now(), closedAt);
    }

    public Conversation withProfileName(String newProfileName) {
        return new Conversation(id, waId, state, status, normalizeProfileName(newProfileName),
                version, createdAt, Instant.now(), closedAt);
    }

    public Conversation withStatus(ConversationStatus newStatus) {
        Instant now = Instant.now();
        return new Conversation(id, waId, state, newStatus, profileName, version, createdAt, now,
                newStatus == ConversationStatus.CLOSED ? now : closedAt);
    }

    public Conversation withVersion(long newVersion) {
        return new Conversation(id, waId, state, status, profileName, newVersion, createdAt,
                updatedAt, closedAt);
    }

    /** Indica si la conversación está cerrada (no admite nuevas interacciones). */
    public boolean isClosed() {
        return status == ConversationStatus.CLOSED;
    }

    /** Indica si la conversación está en un estado terminal (FINAL o CANCELLED). */
    public boolean isTerminal() {
        return state.isTerminal();
    }
}