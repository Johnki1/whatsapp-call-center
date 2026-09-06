package com.botwap.infrastructure.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/** Entidad R2DBC de la tabla {@code conversation_selection}. */
@Table("conversation_selection")
public class ConversationSelectionEntity {

    @Id
    private UUID id;
    @Column("conversation_id")
    private UUID conversationId;
    @Column("level")
    private Integer level;
    @Column("state_key")
    private String stateKey;
    @Column("option_key")
    private String optionKey;
    @Column("display_label")
    private String displayLabel;
    /** JSON serializado (columna JSONB). */
    @Column("metadata")
    private String metadata;
    @Column("selected_at")
    private Instant selectedAt;

    public ConversationSelectionEntity() {
    }

    public ConversationSelectionEntity(UUID id, UUID conversationId, Integer level, String stateKey,
                                       String optionKey, String displayLabel, String metadata,
                                       Instant selectedAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.level = level;
        this.stateKey = stateKey;
        this.optionKey = optionKey;
        this.displayLabel = displayLabel;
        this.metadata = metadata;
        this.selectedAt = selectedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public void setConversationId(UUID conversationId) {
        this.conversationId = conversationId;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public String getStateKey() {
        return stateKey;
    }

    public void setStateKey(String stateKey) {
        this.stateKey = stateKey;
    }

    public String getOptionKey() {
        return optionKey;
    }

    public void setOptionKey(String optionKey) {
        this.optionKey = optionKey;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    public void setDisplayLabel(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public Instant getSelectedAt() {
        return selectedAt;
    }

    public void setSelectedAt(Instant selectedAt) {
        this.selectedAt = selectedAt;
    }
}