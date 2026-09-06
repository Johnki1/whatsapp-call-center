package com.botwap.infrastructure.persistence.adapter;

import com.botwap.domain.model.OutboxMessage;
import com.botwap.domain.model.OutboxStatus;
import com.botwap.domain.port.OutboxRepository;
import com.botwap.infrastructure.persistence.repository.ReactiveOutboxEntityRepository;
import io.r2dbc.spi.Row;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Adaptador R2DBC del puerto {@link OutboxRepository}.
 *
 * <p>El reclamo {@code PENDING → SENDING} se realiza con una sola sentencia
 * {@code UPDATE ... RETURNING} sobre un sub-query con
 * {@code FOR UPDATE SKIP LOCKED} (atómico) y fija un lease de envío; las filas
 * {@code SENDING} con el lease vencido se recuperan en futuros reclamos
 * (crash recovery).</p>
 */
@Component
public class R2dbcOutboxRepositoryAdapter implements OutboxRepository {

    private final ReactiveOutboxEntityRepository repository;
    private final DatabaseClient databaseClient;

    public R2dbcOutboxRepositoryAdapter(ReactiveOutboxEntityRepository repository,
                                        DatabaseClient databaseClient) {
        this.repository = repository;
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<OutboxMessage> save(OutboxMessage outboxMessage) {
        // Upsert por id (el SQL usa CAST(:payload AS JSONB) para la columna jsonb).
        return repository.upsert(
                        outboxMessage.id(),
                        outboxMessage.messageId(),
                        outboxMessage.conversationId(),
                        outboxMessage.waId(),
                        outboxMessage.payload(),
                        outboxMessage.status().name(),
                        outboxMessage.attempts(),
                        OffsetDateTime.ofInstant(outboxMessage.nextAttemptAt(), ZoneOffset.UTC),
                        outboxMessage.leaseExpiresAt() == null
                                ? null
                                : OffsetDateTime.ofInstant(outboxMessage.leaseExpiresAt(), ZoneOffset.UTC),
                        outboxMessage.lastError(),
                        OffsetDateTime.ofInstant(outboxMessage.createdAt(), ZoneOffset.UTC),
                        OffsetDateTime.ofInstant(outboxMessage.updatedAt(), ZoneOffset.UTC),
                        outboxMessage.sentAt() == null
                                ? null
                                : OffsetDateTime.ofInstant(outboxMessage.sentAt(), ZoneOffset.UTC))
                .map(rows -> outboxMessage);
    }

    @Override
    public Flux<OutboxMessage> claimPending(int limit, boolean includeLeaseExpired) {
        return databaseClient.sql("""
                        UPDATE outbox_message
                        SET status = 'SENDING',
                            lease_expires_at = now() + interval '60 seconds',
                            updated_at = now()
                        WHERE id IN (
                            SELECT id FROM outbox_message
                            WHERE (status = 'PENDING' AND next_attempt_at <= now())
                               OR (:includeExpired AND status = 'SENDING' AND lease_expires_at <= now())
                            ORDER BY created_at
                            LIMIT :limit
                            FOR UPDATE SKIP LOCKED
                        )
                        RETURNING id, message_id, conversation_id, wa_id,
                                  CAST(payload AS TEXT) AS payload,
                                  status, attempts, next_attempt_at, lease_expires_at, last_error,
                                  created_at, updated_at, sent_at
                        """)
                .bind("limit", limit)
                .bind("includeExpired", includeLeaseExpired)
                .map((row, rowMetadata) -> toDomain(row))
                .all();
    }

    /** Mapea una fila del {@code RETURNING} del reclamo (DatabaseClient). */
    private OutboxMessage toDomain(Row row) {
        return new OutboxMessage(
                row.get("id", UUID.class),
                row.get("message_id", UUID.class),
                row.get("conversation_id", UUID.class),
                row.get("wa_id", String.class),
                row.get("payload", String.class),
                OutboxStatus.valueOf(row.get("status", String.class)),
                toShort(row.get("attempts", Object.class)),
                toInstant(row.get("next_attempt_at", Object.class)),
                toInstant(row.get("lease_expires_at", Object.class)),
                row.get("last_error", String.class),
                toInstant(row.get("created_at", Object.class)),
                toInstant(row.get("updated_at", Object.class)),
                toInstant(row.get("sent_at", Object.class)));
    }

    private static Short toShort(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.shortValue();
        }
        return Short.parseShort(value.toString());
    }

    private static Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.atOffset(ZoneOffset.UTC).toInstant();
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new IllegalArgumentException("Tipo de fecha inesperado: " + value.getClass());
    }
}