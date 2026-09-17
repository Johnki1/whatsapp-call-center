package com.botwap.infrastructure.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/** Entidad R2DBC de la tabla {@code conversation}. */
@Table("conversation")
public class ConversationEntity {

    @Id
    private UUID id;
    @Column("wa_id")
    private String waId;
    @Column("state")
    private String state;
    @Column("status")
    private String status;
    @Column("profile_name")
    private String profileName;
    @Column("version")
    private long version;
    @Column("created_at")
    private Instant createdAt;
    @Column("updated_at")
    private Instant updatedAt;
    @Column("closed_at")
    private Instant closedAt;

    @Column("last_interaction_at")
    private Instant lastInteractionAt;

    public Instant getLastInteractionAt() { return lastInteractionAt; }
    public void setLastInteractionAt(Instant value) { this.lastInteractionAt = value; }

    @Column("last_inbound_at")
    private Instant lastInboundAt;

    public Instant getLastInboundAt() { return lastInboundAt; }
    public void setLastInboundAt(Instant value) { this.lastInboundAt = value; }

    @Column("last_bot_message_at")
    private Instant lastBotMessageAt;

    public Instant getLastBotMessageAt() { return lastBotMessageAt; }
    public void setLastBotMessageAt(Instant value) { this.lastBotMessageAt = value; }

    @Column("reminder_at")
    private Instant reminderAt;

    public Instant getReminderAt() { return reminderAt; }
    public void setReminderAt(Instant value) { this.reminderAt = value; }

    @Column("reengagement_pending")
    private boolean reengagementPending;

    public boolean getReengagementPending() { return reengagementPending; }
    public void setReengagementPending(boolean value) { this.reengagementPending = value; }

    @Column("last_prompt_payload")
    private String lastPromptPayload;

    public String getLastPromptPayload() { return lastPromptPayload; }
    public void setLastPromptPayload(String value) { this.lastPromptPayload = value; }

    @Column("awaiting_reply_message_id")
    private UUID awaitingReplyMessageId;

    public UUID getAwaitingReplyMessageId() { return awaitingReplyMessageId; }
    public void setAwaitingReplyMessageId(UUID value) { this.awaitingReplyMessageId = value; }

    public ConversationEntity() {
    }

    public ConversationEntity(UUID id, String waId, String state, String status, long version,
                              Instant createdAt, Instant updatedAt, Instant closedAt) {
        this.id = id;
        this.waId = waId;
        this.state = state;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.closedAt = closedAt;
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getWaId() {
        return waId;
    }

    public void setWaId(String waId) {
        this.waId = waId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }
}