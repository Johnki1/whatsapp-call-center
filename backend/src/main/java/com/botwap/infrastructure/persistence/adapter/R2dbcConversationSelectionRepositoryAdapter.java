package com.botwap.infrastructure.persistence.adapter;

import com.botwap.domain.model.ConversationSelection;
import com.botwap.domain.port.ConversationSelectionRepository;
import com.botwap.infrastructure.persistence.entity.ConversationSelectionEntity;
import com.botwap.infrastructure.persistence.repository.ReactiveConversationSelectionEntityRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador R2DBC del puerto {@link ConversationSelectionRepository}.
 *
 * <p>El {@code save} es un <em>upsert</em> por nivel
 * ({@code ON CONFLICT (conversation_id, level)}): navegar de vuelta actualiza
 * la selección del nivel en lugar de duplicarla. Los metadatos se serializan
 * a JSON (columna JSONB).</p>
 */
@Component
public class R2dbcConversationSelectionRepositoryAdapter
        implements ConversationSelectionRepository {

    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private final ReactiveConversationSelectionEntityRepository repository;
    private final ObjectMapper objectMapper;

    public R2dbcConversationSelectionRepositoryAdapter(
            ReactiveConversationSelectionEntityRepository repository,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Flux<ConversationSelection> findByConversationId(UUID conversationId) {
        return repository.findByConversationId(conversationId).map(this::toDomain);
    }

    @Override
    public Mono<ConversationSelection> save(ConversationSelection selection) {
        return repository.upsert(
                        selection.id(),
                        selection.conversationId(),
                        selection.level(),
                        selection.stateKey(),
                        selection.optionKey(),
                        selection.displayLabel(),
                        serialize(selection.metadata()),
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
                deserialize(e.getMetadata()),
                e.getSelectedAt());
    }

    private String serialize(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception ex) {
            throw new IllegalArgumentException("No se pudo serializar los metadatos de la selección", ex);
        }
    }

    private Map<String, String> deserialize(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return new LinkedHashMap<>(objectMapper.readValue(json, STRING_MAP));
        } catch (Exception ex) {
            throw new IllegalArgumentException("No se pudo deserializar los metadatos de la selección", ex);
        }
    }
}