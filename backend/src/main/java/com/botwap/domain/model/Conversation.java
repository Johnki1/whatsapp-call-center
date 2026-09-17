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
        Instant closedAt,
        Instant lastInteractionAt,
        Instant lastInboundAt,
        Instant lastBotMessageAt,
        Instant reminderAt,
        boolean reengagementPending,
        String lastPromptPayload,
        UUID awaitingReplyMessageId) {

    /** Compatibilidad para conversaciones sin actividad registrada todavía. */
    public Conversation(UUID id, String waId, ConversationState state, ConversationStatus status,
                        String profileName, long version, Instant createdAt, Instant updatedAt,
                        Instant closedAt) {
        this(id, waId, state, status, profileName, version, createdAt, updatedAt, closedAt,
                updatedAt, null, null, null, false, null, null);
    }

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
                Instant.now(), closedAt, lastInteractionAt, lastInboundAt, lastBotMessageAt,
                reminderAt, reengagementPending, lastPromptPayload, awaitingReplyMessageId);
    }

    public Conversation withProfileName(String newProfileName) {
        return new Conversation(id, waId, state, status, normalizeProfileName(newProfileName),
                version, createdAt, Instant.now(), closedAt, lastInteractionAt, lastInboundAt,
                lastBotMessageAt, reminderAt, reengagementPending, lastPromptPayload, awaitingReplyMessageId);
    }

    public Conversation withStatus(ConversationStatus newStatus) {
        Instant now = Instant.now();
        return new Conversation(id, waId, state, newStatus, profileName, version, createdAt, now,
                newStatus == ConversationStatus.CLOSED ? now : closedAt, lastInteractionAt,
                lastInboundAt, lastBotMessageAt, reminderAt, reengagementPending,
                lastPromptPayload, awaitingReplyMessageId);
    }

    public Conversation withVersion(long newVersion) {
        return new Conversation(id, waId, state, status, profileName, newVersion, createdAt,
                updatedAt, closedAt, lastInteractionAt, lastInboundAt, lastBotMessageAt,
                reminderAt, reengagementPending, lastPromptPayload, awaitingReplyMessageId);
    }

    /** Un entrante invalida cualquier espera previa, pero conserva el menú suspendido. */
    public Conversation receivedAt(Instant now) {
        return new Conversation(id, waId, state, status, profileName, version, createdAt, now,
                closedAt, now, now, null, null, reengagementPending, lastPromptPayload, null);
    }

    /** El plazo no comienza hasta que el Outbox confirma el envío de este mensaje. */
    public Conversation awaitingReply(UUID messageId, String payload, boolean pending, Instant reminder) {
        return new Conversation(id, waId, state, status, profileName, version, createdAt,
                Instant.now(), closedAt, lastInteractionAt, lastInboundAt, null, reminder,
                pending, payload, messageId);
    }

    public boolean requiresReengagement(Instant now, java.time.Duration delay) {
        return !isClosed() && !isTerminal() && (reengagementPending
                || (reminderAt != null && lastBotMessageAt != null
                && !now.isBefore(reminderAt.plus(delay))));
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