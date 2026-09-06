package com.botwap.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Selección de una opción en un nivel de la conversación.
 *
 * <p>Una fila por nivel navegado (tabla {@code conversation_selection},
 * única por {@code (conversation_id, level)}). La navegación de vuelta
 * hace <em>upsert</em> del nivel.</p>
 */
public record ConversationSelection(
        UUID id,
        UUID conversationId,
        int level,
        String stateKey,
        String optionKey,
        String displayLabel,
        Map<String, String> metadata,
        Instant selectedAt) {

    public ConversationSelection {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(stateKey, "stateKey");
        Objects.requireNonNull(optionKey, "optionKey");
        Objects.requireNonNull(displayLabel, "displayLabel");
        Objects.requireNonNull(selectedAt, "selectedAt");
    }

    /** Crea una selección sin metadatos adicionales. */
    public static ConversationSelection of(UUID conversationId, int level, String stateKey,
                                           String optionKey, String displayLabel) {
        return new ConversationSelection(UUID.randomUUID(), conversationId, level, stateKey,
                optionKey, displayLabel, Map.of(), Instant.now());
    }

    public ConversationSelection withMetadata(Map<String, String> extraMetadata) {
        return new ConversationSelection(id, conversationId, level, stateKey, optionKey,
                displayLabel, extraMetadata == null ? Map.of() : Map.copyOf(extraMetadata), selectedAt);
    }
}