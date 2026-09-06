package com.botwap.infrastructure.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/** Entidad R2DBC de la tabla {@code message}. */
@Table("message")
public class MessageEntity {

    @Id
    private UUID id;
    @Column("conversation_id")
    private UUID conversationId;
    @Column("wa_message_id")
    private String waMessageId;
    @Column("direction")
    private String direction;
    @Column("status")
    private String status;
    @Column("type")
    private String type;
    @Column("content")
    private String content;
    @Column("created_at")
    private Instant createdAt;
    @Column("sent_at")
    private Instant sentAt;

    public MessageEntity() {
    }

    public MessageEntity(UUID id, UUID conversationId, String waMessageId, String direction,
                         String status, String type, String content, Instant createdAt, Instant sentAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.waMessageId = waMessageId;
        this.direction = direction;
        this.status = status;
        this.type = type;
        this.content = content;
        this.createdAt = createdAt;
        this.sentAt = sentAt;
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

    public String getWaMessageId() {
        return waMessageId;
    }

    public void setWaMessageId(String waMessageId) {
        this.waMessageId = waMessageId;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }
}