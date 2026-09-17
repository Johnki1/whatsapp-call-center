package com.botwap.infrastructure.persistence.repository;

import com.botwap.infrastructure.persistence.entity.ConversationEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Acceso R2DBC a {@code conversation}.
 *
 * <p>El insert/update se realizan con SQL explícito para controlar el
 * optimistic lock ({@code version}) de forma determinística. Los métodos DML
 * requieren {@code @Modifying} (docs de Spring Data R2DBC).</p>
 */
public interface ReactiveConversationEntityRepository
        extends ReactiveCrudRepository<ConversationEntity, UUID> {

    String SELECT_ALL = """
            SELECT id, wa_id, state, status, profile_name, version, created_at, updated_at, closed_at,
                   last_interaction_at, last_inbound_at, last_bot_message_at, reminder_at, reengagement_pending, last_prompt_payload, awaiting_reply_message_id
            FROM conversation
            """;

    @Query(SELECT_ALL + "WHERE wa_id = :waId AND status = 'ACTIVE'")
    Mono<ConversationEntity> findActiveByWaId(@Param("waId") String waId);

    @Query(SELECT_ALL + "WHERE wa_id = :waId AND status = 'ACTIVE' FOR UPDATE")
    Mono<ConversationEntity> findActiveByWaIdForUpdate(@Param("waId") String waId);

    @Query(SELECT_ALL + "WHERE wa_id = :waId ORDER BY created_at DESC, id DESC LIMIT 1 FOR UPDATE")
    Mono<ConversationEntity> findLatestByWaIdForUpdate(@Param("waId") String waId);

    @Query(SELECT_ALL + """
            WHERE status = 'ACTIVE' AND state NOT IN ('FINAL', 'CANCELLED', 'HUMAN_AGENT')
              AND reminder_at IS NULL AND NOT reengagement_pending
              AND last_bot_message_at <= :cutoff AND last_prompt_payload IS NOT NULL
              AND last_inbound_at > :windowStart
              AND (last_inbound_at IS NULL OR last_bot_message_at >= last_inbound_at)
            ORDER BY last_bot_message_at LIMIT :limit FOR UPDATE SKIP LOCKED
            """)
    reactor.core.publisher.Flux<ConversationEntity> findReminderDueForUpdate(
            @Param("cutoff") OffsetDateTime cutoff, @Param("windowStart") OffsetDateTime windowStart,
            @Param("limit") int limit);

    @Modifying
    @Query("""
            INSERT INTO conversation (id, wa_id, state, status, version, created_at, updated_at, closed_at, profile_name, last_interaction_at, last_inbound_at, last_bot_message_at, reminder_at, reengagement_pending, last_prompt_payload, awaiting_reply_message_id)
            VALUES (:id, :waId, :state, :status, :version, :createdAt, :updatedAt, :closedAt, :profileName, :lastInteractionAt, :lastInboundAt, :lastBotMessageAt, :reminderAt, :reengagementPending, :lastPromptPayload, :awaitingReplyMessageId)
            """
    )
    Mono<Integer> insertConversation(@Param("id") UUID id,
                                     @Param("waId") String waId,
                                     @Param("state") String state,
                                     @Param("status") String status,
                                     @Param("version") long version,
                                     @Param("createdAt") OffsetDateTime createdAt,
                                     @Param("updatedAt") OffsetDateTime updatedAt,
                                     @Param("closedAt") OffsetDateTime closedAt,
                                     @Param("profileName") String profileName,
                                     @Param("lastInteractionAt") OffsetDateTime lastInteractionAt,
                                     @Param("lastInboundAt") OffsetDateTime lastInboundAt,
                                     @Param("lastBotMessageAt") OffsetDateTime lastBotMessageAt,
                                     @Param("reminderAt") OffsetDateTime reminderAt,
                                     @Param("reengagementPending") boolean reengagementPending,
                                     @Param("lastPromptPayload") String lastPromptPayload,
                                     @Param("awaitingReplyMessageId") UUID awaitingReplyMessageId);

    @Modifying
    @Query("""
            UPDATE conversation
            SET state = :state,
                status = :status,
                version = :newVersion,
                updated_at = :updatedAt,
                closed_at = :closedAt,
                profile_name = COALESCE(:profileName, profile_name),
                last_interaction_at = :lastInteractionAt,
                last_inbound_at = :lastInboundAt,
                last_bot_message_at = :lastBotMessageAt,
                reminder_at = :reminderAt,
                reengagement_pending = :reengagementPending,
                last_prompt_payload = :lastPromptPayload,
                awaiting_reply_message_id = :awaitingReplyMessageId
            WHERE id = :id AND version = :expectedVersion
            """)
    Mono<Integer> updateConversation(@Param("id") UUID id,
                                     @Param("state") String state,
                                     @Param("status") String status,
                                     @Param("newVersion") long newVersion,
                                     @Param("updatedAt") OffsetDateTime updatedAt,
                                     @Param("closedAt") OffsetDateTime closedAt,
                                     @Param("profileName") String profileName,
                                     @Param("expectedVersion") long expectedVersion,
                                     @Param("lastInteractionAt") OffsetDateTime lastInteractionAt,
                                     @Param("lastInboundAt") OffsetDateTime lastInboundAt,
                                     @Param("lastBotMessageAt") OffsetDateTime lastBotMessageAt,
                                     @Param("reminderAt") OffsetDateTime reminderAt,
                                     @Param("reengagementPending") boolean reengagementPending,
                                     @Param("lastPromptPayload") String lastPromptPayload,
                                     @Param("awaitingReplyMessageId") UUID awaitingReplyMessageId);
}