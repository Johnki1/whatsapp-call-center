package com.botwap.infrastructure.persistence.repository;

import com.botwap.infrastructure.persistence.entity.MessageEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Acceso R2DBC a {@code message}. */
public interface ReactiveMessageEntityRepository
        extends ReactiveCrudRepository<MessageEntity, UUID> {

    /** Transición idempotente {@code RECEIVED → PROCESSED} (deduplicación). */
    @Modifying
    @Query("UPDATE message SET status = 'PROCESSED' WHERE id = :id AND status = 'RECEIVED'")
    Mono<Integer> markReceivedAsProcessed(@Param("id") UUID id);

    @Query("SELECT EXISTS (SELECT 1 FROM message WHERE wa_message_id = :waMessageId)")
    Mono<Boolean> existsByWaMessageId(@Param("waMessageId") String waMessageId);
}