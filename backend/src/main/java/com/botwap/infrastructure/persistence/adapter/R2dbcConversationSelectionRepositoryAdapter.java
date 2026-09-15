package com.botwap.infrastructure.persistence.adapter;

import com.botwap.domain.model.ConversationSelection;
import com.botwap.domain.port.ConversationSelectionRepository;
import com.botwap.infrastructure.persistence.entity.ConversationSelectionEntity;
import com.botwap.infrastructure.persistence.repository.ReactiveConversationSelectionEntityRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Adaptador R2DBC del puerto ConversationSelectionRepository.
 *
 * <p>El save es un upsert por nivel (ON CONFLICT (conversation_id, level)).
 * Los metadatos se pasan como String JSON (columna JSONB).</p>
 */
@Component
public class R2dbcConversationSelectionRepositoryAdapter
        implements ConversationSelectionRepository {

    private final ReactiveConversationSelectionEntityRepository repository;

    public R2dbcConversationSelectionRepositoryAdapter(
            ReactiveConversationSelectionEntityRepository repository) {
        this.repository = repository;
    }

    @Override
    public Flux<ConversationSelection> findByConversationId(UUID conversationId) {
        return repository.findByConversationId(conversationId).map(this::toDomain);
    }

    @Override
    public Mono<ConversationSelection> save(ConversationSelection selection) {
        return repository.upsert(
                        selection.id() == null ? UUID.randomUUID() : selection.id(),
                        selection.conversationId(),
                        selection.level(),
                        selection.stateKey(),
                        selection.optionKey(),
                        selection.displayLabel(),
                        selection.metadata() == null ? "{}" : selection.metadata(),
                        OffsetDateTime.ofInstant(selection.selectedAt(), java.time.ZoneOffset.UTC))
                .map(rows -> selection);
    }

    private ConversationSelection toDomain(ConversationSelectionEntity e) {
        return new ConversationSelection(
                e.getId(),
                e.getConversationId(),
                e.getLevel(),
                e.getStateKey(),
                e.getOptionKey(),
                e.getDisplayLabel(),
                e.getMetadata(),
                e.getSelectedAt());
    }
}