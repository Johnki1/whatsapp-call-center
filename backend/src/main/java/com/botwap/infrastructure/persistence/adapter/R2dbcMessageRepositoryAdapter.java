package com.botwap.infrastructure.persistence.adapter;

import com.botwap.domain.model.Message;
import com.botwap.domain.model.MessageDirection;
import com.botwap.domain.model.MessageStatus;
import com.botwap.domain.model.MessageType;
import com.botwap.domain.port.MessageRepository;
import com.botwap.infrastructure.persistence.entity.MessageEntity;
import com.botwap.infrastructure.persistence.repository.ReactiveMessageEntityRepository;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Adaptador R2DBC del puerto {@link MessageRepository}.
 *
 * <p>El {@code wa_message_id} único en base de datos es la red de seguridad de
 * la deduplicación; {@link #markReceivedAsProcessed} materializa la transición
 * idempotente {@code RECEIVED → PROCESSED}.</p>
 *
 * <p>Los mensajes siempre se {@code INSERT} an: el {@code save} de
 * {@code ReactiveCrudRepository} haría UPDATE al venir el {@code @Id}
 * poblado (los UUID se generan en el dominio), por eso se usa
 * {@code R2dbcEntityTemplate.insert}.</p>
 */
@Component
public class R2dbcMessageRepositoryAdapter implements MessageRepository {

    private final ReactiveMessageEntityRepository repository;
    private final R2dbcEntityTemplate entityTemplate;

    public R2dbcMessageRepositoryAdapter(ReactiveMessageEntityRepository repository,
                                         R2dbcEntityTemplate entityTemplate) {
        this.repository = repository;
        this.entityTemplate = entityTemplate;
    }

    @Override
    public Mono<Message> save(Message message) {
        return entityTemplate.insert(toEntity(message)).map(this::toDomain);
    }

    @Override
    public Mono<Message> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Mono<Boolean> existsByWaMessageId(String waMessageId) {
        return repository.existsByWaMessageId(waMessageId);
    }

    @Override
    public Mono<Boolean> markReceivedAsProcessed(UUID messageId) {
        return repository.markReceivedAsProcessed(messageId).map(rows -> rows == 1);
    }

    private Message toDomain(MessageEntity e) {
        return new Message(
                e.getId(),
                e.getConversationId(),
                e.getWaMessageId(),
                MessageDirection.valueOf(e.getDirection()),
                MessageStatus.valueOf(e.getStatus()),
                MessageType.valueOf(e.getType()),
                e.getContent(),
                e.getCreatedAt(),
                e.getSentAt());
    }

    private MessageEntity toEntity(Message m) {
        return new MessageEntity(
                m.id(),
                m.conversationId(),
                m.waMessageId(),
                m.direction().name(),
                m.status().name(),
                m.type().name(),
                m.content(),
                m.createdAt(),
                m.sentAt());
    }
}