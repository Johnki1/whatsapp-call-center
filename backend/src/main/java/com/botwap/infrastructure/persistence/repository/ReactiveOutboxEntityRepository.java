package com.botwap.infrastructure.persistence.repository;

import com.botwap.infrastructure.persistence.entity.OutboxMessageEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Acceso R2DBC a {@code outbox_message} (cola de salida / patrón Outbox).
 *
 * <p>El {@code payload} se inserta/actualiza con {@code CAST(... AS JSONB)}
 * porque el driver R2DBC enlaza el {@code String} y PostgreSQL no lo convierte
 * implícitamente a {@code jsonb} en sentencias preparadas.</p>
 */
public interface ReactiveOutboxEntityRepository
        extends ReactiveCrudRepository<OutboxMessageEntity, UUID> {

    /**
     * Upsert por {@code id}: alta de la respuesta pendiente (Fase A) o
     * actualización de su estado (Fase 5: SENT / FAILED / backoff).
     */
    @Modifying
    @Query("""
            INSERT INTO outbox_message
                (id, message_id, conversation_id, wa_id, payload, status, attempts,
                 next_attempt_at, lease_expires_at, last_error, created_at, updated_at, sent_at)
            VALUES (:id, :messageId, :conversationId, :waId, CAST(:payload AS JSONB), :status, :attempts,
                    :nextAttemptAt, :leaseExpiresAt, :lastError, :createdAt, :updatedAt, :sentAt)
            ON CONFLICT (id) DO UPDATE SET
                payload = EXCLUDED.payload,
                status = EXCLUDED.status,
                attempts = EXCLUDED.attempts,
                next_attempt_at = EXCLUDED.next_attempt_at,
                lease_expires_at = EXCLUDED.lease_expires_at,
                last_error = EXCLUDED.last_error,
                updated_at = EXCLUDED.updated_at,
                sent_at = EXCLUDED.sent_at
            """)
    Mono<Integer> upsert(@Param("id") UUID id,
                         @Param("messageId") UUID messageId,
                         @Param("conversationId") UUID conversationId,
                         @Param("waId") String waId,
                         @Param("payload") String payload,
                         @Param("status") String status,
                         @Param("attempts") int attempts,
                         @Param("nextAttemptAt") OffsetDateTime nextAttemptAt,
                         @Param("leaseExpiresAt") OffsetDateTime leaseExpiresAt,
                         @Param("lastError") String lastError,
                         @Param("createdAt") OffsetDateTime createdAt,
                         @Param("updatedAt") OffsetDateTime updatedAt,
                         @Param("sentAt") OffsetDateTime sentAt);
}