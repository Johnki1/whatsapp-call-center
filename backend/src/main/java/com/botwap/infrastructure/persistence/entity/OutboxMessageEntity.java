package com.botwap.infrastructure.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/** Entidad R2DBC de la tabla {@code outbox_message} (patrón Outbox). */
@Table("outbox_message")
public class OutboxMessageEntity {

    @Id
    private UUID id;
    @Column("message_id")
    private UUID messageId;
    @Column("conversation_id")
    private UUID conversationId;
    @Column("wa_id")
    private String waId;
    /** JSON serializado (columna JSONB). */
    @Column("payload")
    private String payload;
    @Column("status")
    private String status;
    @Column("attempts")
    private Integer attempts;
    @Column("next_attempt_at")
    private Instant nextAttemptAt;
    @Column("lease_expires_at")
    private Instant leaseExpiresAt;
    @Column("last_error")
    private String lastError;
    @Column("created_at")
    private Instant createdAt;
    @Column("updated_at")
    private Instant updatedAt;
    @Column("sent_at")
    private Instant sentAt;

    public OutboxMessageEntity() {
    }

    public OutboxMessageEntity(UUID id, UUID messageId, UUID conversationId, String waId, String payload,
                               String status, Integer attempts, Instant nextAttemptAt, Instant leaseExpiresAt,
                               String lastError, Instant createdAt, Instant updatedAt, Instant sentAt) {
        this.id = id;
        this.messageId = messageId;
        this.conversationId = conversationId;
        this.waId = waId;
        this.payload = payload;
        this.status = status;
        this.attempts = attempts;
        this.nextAttemptAt = nextAttemptAt;
        this.leaseExpiresAt = leaseExpiresAt;
        this.lastError = lastError;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.sentAt = sentAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public void setMessageId(UUID messageId) {
        this.messageId = messageId;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public void setConversationId(UUID conversationId) {
        this.conversationId = conversationId;
    }

    public String getWaId() {
        return waId;
    }

    public void setWaId(String waId) {
        this.waId = waId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getAttempts() {
        return attempts;
    }

    public void setAttempts(Integer attempts) {
        this.attempts = attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public Instant getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public void setLeaseExpiresAt(Instant leaseExpiresAt) {
        this.leaseExpiresAt = leaseExpiresAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
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

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }
}