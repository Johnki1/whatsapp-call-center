package com.botwap.infrastructure.persistence.adapter;

import com.botwap.application.exception.ConcurrencyConflictException;
import com.botwap.application.exception.DomainException;
import com.botwap.domain.model.Conversation;
import com.botwap.domain.model.ConversationState;
import com.botwap.domain.model.ConversationStatus;
import com.botwap.domain.port.ConversationRepository;
import com.botwap.infrastructure.persistence.entity.ConversationEntity;
import com.botwap.infrastructure.persistence.repository.ReactiveConversationEntityRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Adaptador R2DBC del puerto {@link ConversationRepository}.
 *
 * <p>La persistencia es explícita (SQL): el {@code save} inserta si la fila no
 * existe o actualiza con control de versión (optimistic lock) si existe, de
 * modo que la transición de estado siempre es segura ante concurrencia.</p>
 */
@Component
public class R2dbcConversationRepositoryAdapter implements ConversationRepository {

    private final ReactiveConversationEntityRepository repository;

    public R2dbcConversationRepositoryAdapter(ReactiveConversationEntityRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<Conversation> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Mono<Conversation> findActiveByWaId(String waId) {
        return repository.findActiveByWaId(waId).map(this::toDomain);
    }

    @Override
    public Mono<Conversation> findActiveByWaIdForUpdate(String waId) {
        return repository.findActiveByWaIdForUpdate(waId).map(this::toDomain);
    }

    @Override
    public Mono<Conversation> save(Conversation conversation) {
        return repository.findById(conversation.id())
                .flatMap(existing -> update(conversation))
                .switchIfEmpty(insert(conversation));
    }

    private Mono<Conversation> insert(Conversation c) {
        return repository.insertConversation(
                        c.id(),
                        c.waId(),
                        c.state().name(),
                        c.status().name(),
                        0L,
                        toOffset(c.createdAt()),
                        toOffset(c.updatedAt()),
                        toOffsetOrNull(c.closedAt()))
                .map(rows -> c.withVersion(0L))
                .onErrorMap(DataIntegrityViolationException.class,
                        e -> new DomainException("No se pudo crear la conversación (¿ya existe una activa?)", e));
    }

    private Mono<Conversation> update(Conversation c) {
        long newVersion = c.version() + 1;
        Instant now = Instant.now();
        return repository.updateConversation(
                        c.id(),
                        c.state().name(),
                        c.status().name(),
                        newVersion,
                        toOffset(now),
                        toOffsetOrNull(c.closedAt()),
                        c.version())
                .flatMap(rows -> rows == 1
                        ? Mono.just(new Conversation(c.id(), c.waId(), c.state(), c.status(),
                                newVersion, c.createdAt(), now, c.closedAt()))
                        : Mono.error(new ConcurrencyConflictException(c.id())));
    }

    private Conversation toDomain(ConversationEntity e) {
        return new Conversation(
                e.getId(),
                e.getWaId(),
                ConversationState.valueOf(e.getState()),
                ConversationStatus.valueOf(e.getStatus()),
                e.getVersion(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getClosedAt());
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant != null ? OffsetDateTime.ofInstant(instant, java.time.ZoneOffset.UTC) : null;
    }

    private static OffsetDateTime toOffsetOrNull(Instant instant) {
        return toOffset(instant);
    }
}