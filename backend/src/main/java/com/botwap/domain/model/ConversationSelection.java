package com.botwap.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Seleccion de una opcion en un nivel de la conversacion.
 *
 * <p>Una fila por nivel navegado (tabla {@code conversation_selection},
 * unica por {@code (conversation_id, level)}). La navegacion de vuelta
 * hace upsert del nivel.</p>
 *
 * <p>El {@code metadata} es un JSON serializado (columna JSONB) que permite
 * almacenar informacion adicional por seleccion sin cambiar el esquema
 * (ej. tipo de documento, numero enmascarado).</p>
 */
public record ConversationSelection(
        UUID id,
        UUID conversationId,
        int level,
        String stateKey,
        String optionKey,
        String displayLabel,
        String metadata,
        Instant selectedAt) {

    public ConversationSelection {
        Objects.requireNonNull(stateKey, "stateKey");
        Objects.requireNonNull(optionKey, "optionKey");
        Objects.requireNonNull(displayLabel, "displayLabel");
    }

    /**
     * Crea una seleccion preliminar (pre-persistencia): el {@code id},
     * {@code conversationId} y {@code selectedAt} se completan al momento
     * de persistir (el repositorio genera el UUID y el orquestador asigna
     * la conversacion y el timestamp).
     */
    public static ConversationSelection unpersisted(int level, String stateKey,
                                                    String optionKey, String displayLabel,
                                                    String metadata) {
        return new ConversationSelection(null, null, level, stateKey, optionKey,
                displayLabel, metadata, null);
    }

    /**
     * Crea una seleccion ya asociada a una conversacion (usado por tests y
     * codigo que ya conoce el id de conversacion).
     */
    public static ConversationSelection of(UUID conversationId, int level, String stateKey,
                                           String optionKey, String displayLabel) {
        return new ConversationSelection(UUID.randomUUID(), conversationId, level, stateKey,
                optionKey, displayLabel, "{}", Instant.now());
    }

    public ConversationSelection withMetadata(String metadataJson) {
        return new ConversationSelection(id, conversationId, level, stateKey, optionKey,
                displayLabel, metadataJson == null ? "{}" : metadataJson, selectedAt);
    }

    public ConversationSelection withConversationId(UUID convId) {
        return new ConversationSelection(id, convId, level, stateKey, optionKey,
                displayLabel, metadata, selectedAt);
    }

    public ConversationSelection withSelectedAt(Instant newSelectedAt) {
        return new ConversationSelection(id, conversationId, level, stateKey, optionKey,
                displayLabel, metadata, newSelectedAt);
    }
}
