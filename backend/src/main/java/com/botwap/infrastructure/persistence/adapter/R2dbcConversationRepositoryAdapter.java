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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Adaptador R2DBC del puerto {@link ConversationRepository}.
 *
 * <p>La persistencia es explícita (SQL): {@code insert} crea una nueva fila,
 * {@code update} actualiza con control de versión (optimistic lock) de modo
 * que la transición de estado siempre es segura ante concurrencia.</p>
 */
@Component
public class R2dbcConversationRepositoryAdapter implements ConversationRepository {

    private static final Logger log = LoggerFactory.getLogger(R2dbcConversationRepositoryAdapter.class);

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
    public Mono<Conversation> findLatestByWaIdForUpdate(String waId) {
        return repository.findLatestByWaIdForUpdate(waId).map(this::toDomain);
    }

    @Override
    public Mono<Conversation> insert(Conversation conversation) {
        log.warn("DIAG save: INSERT branch, id={} newState={}", conversation.id(), conversation.state());
        return repository.insertConversation(
                        conversation.id(),
                        conversation.waId(),
                        conversation.state().name(),
                        conversation.status().name(),
                        0L,
                        toOffset(conversation.createdAt()),
                        toOffset(conversation.updatedAt()),
                        toOffsetOrNull(conversation.closedAt()),
                        conversation.profileName())
                .map(rows -> conversation.withVersion(0L))
                .onErrorMap(DataIntegrityViolationException.class,
                        e -> new DomainException("No se pudo crear la conversación (¿ya existe una activa?)", e));
    }

    @Override
    public Mono<Conversation> update(Conversation conversation) {
        log.warn("DIAG save: UPDATE branch, id={} newState={} version={}",
                conversation.id(), conversation.state(), conversation.version());
        long newVersion = conversation.version() + 1;
        Instant now = Instant.now();
        return repository.updateConversation(
                        conversation.id(),
                        conversation.state().name(),
                        conversation.status().name(),
                        newVersion,
                        toOffset(now),
                        toOffsetOrNull(conversation.closedAt()),
                        conversation.profileName(),
                        conversation.version())
                .flatMap(rows -> {
                    log.warn("DIAG update: id={} rows={} newState={} expectedVersion={}",
                            conversation.id(), rows, conversation.state(), conversation.version());
                    return rows == 1
                            ? Mono.just(new Conversation(conversation.id(), conversation.waId(), conversation.state(), conversation.status(),
                                    conversation.profileName(), newVersion, conversation.createdAt(), now, conversation.closedAt()))
                            : Mono.error(new ConcurrencyConflictException(conversation.id()));
                });
    }

    private Conversation toDomain(ConversationEntity e) {
        return new Conversation(
                e.getId(),
                e.getWaId(),
                ConversationState.valueOf(e.getState()),
                ConversationStatus.valueOf(e.getStatus()),
                e.getProfileName(),
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
