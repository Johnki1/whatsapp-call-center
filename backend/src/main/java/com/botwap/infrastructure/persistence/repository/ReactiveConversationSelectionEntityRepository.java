package com.botwap.infrastructure.persistence.repository;

import com.botwap.infrastructure.persistence.entity.ConversationSelectionEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Acceso R2DBC a {@code conversation_selection}.
 *
 * <p>Una fila por nivel: el {@code upsert} usa {@code ON CONFLICT
 * (conversation_id, level)} (índice único) para actualizar la selección del
 * nivel al navegar de vuelta.</p>
 */
public interface ReactiveConversationSelectionEntityRepository
        extends ReactiveCrudRepository<ConversationSelectionEntity, UUID> {

    @Query("""
            SELECT id, conversation_id, level, state_key, option_key, display_label,
                   CAST(metadata AS TEXT) AS metadata, selected_at
            FROM conversation_selection
            WHERE conversation_id = :conversationId
            """)
    Flux<ConversationSelectionEntity> findByConversationId(@Param("conversationId") UUID conversationId);

    @Modifying
    @Query("""
            INSERT INTO conversation_selection
                (id, conversation_id, level, state_key, option_key, display_label, metadata, selected_at)
            VALUES (:id, :conversationId, :level, :stateKey, :optionKey, :displayLabel,
                    CAST(:metadata AS JSONB), :selectedAt)
            ON CONFLICT (conversation_id, level, state_key) DO UPDATE SET
                option_key = EXCLUDED.option_key,
                display_label = EXCLUDED.display_label,
                metadata = EXCLUDED.metadata,
                selected_at = EXCLUDED.selected_at
            """)
    Mono<Integer> upsert(@Param("id") UUID id,
                         @Param("conversationId") UUID conversationId,
                         @Param("level") Integer level,
                         @Param("stateKey") String stateKey,
                         @Param("optionKey") String optionKey,
                         @Param("displayLabel") String displayLabel,
                         @Param("metadata") String metadata,
                         @Param("selectedAt") OffsetDateTime selectedAt);
}