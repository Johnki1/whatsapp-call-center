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
            SELECT id, wa_id, state, status, version, created_at, updated_at, closed_at
            FROM conversation
            """;

    @Query(SELECT_ALL + "WHERE wa_id = :waId AND status = 'ACTIVE'")
    Mono<ConversationEntity> findActiveByWaId(@Param("waId") String waId);

    @Query(SELECT_ALL + "WHERE wa_id = :waId AND status = 'ACTIVE' FOR UPDATE")
    Mono<ConversationEntity> findActiveByWaIdForUpdate(@Param("waId") String waId);

    @Modifying
    @Query("""
            INSERT INTO conversation (id, wa_id, state, status, version, created_at, updated_at, closed_at)
            VALUES (:id, :waId, :state, :status, :version, :createdAt, :updatedAt, :closedAt)
            """)
    Mono<Integer> insertConversation(@Param("id") UUID id,
                                     @Param("waId") String waId,
                                     @Param("state") String state,
                                     @Param("status") String status,
                                     @Param("version") long version,
                                     @Param("createdAt") OffsetDateTime createdAt,
                                     @Param("updatedAt") OffsetDateTime updatedAt,
                                     @Param("closedAt") OffsetDateTime closedAt);

    @Modifying
    @Query("""
            UPDATE conversation
            SET state = :state,
                status = :status,
                version = :newVersion,
                updated_at = :updatedAt,
                closed_at = :closedAt
            WHERE id = :id AND version = :expectedVersion
            """)
    Mono<Integer> updateConversation(@Param("id") UUID id,
                                     @Param("state") String state,
                                     @Param("status") String status,
                                     @Param("newVersion") long newVersion,
                                     @Param("updatedAt") OffsetDateTime updatedAt,
                                     @Param("closedAt") OffsetDateTime closedAt,
                                     @Param("expectedVersion") long expectedVersion);
}