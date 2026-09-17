package com.botwap.application.service;

import com.botwap.domain.engine.EngineResult;
import com.botwap.domain.model.Conversation;
import com.botwap.domain.model.Message;
import com.botwap.domain.model.OutboxMessage;
import com.botwap.domain.port.ConversationRepository;
import com.botwap.domain.port.MessageRepository;
import com.botwap.domain.port.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** El lock y la marca de recordatorio se confirman junto al mensaje y su outbox. */
@Service
public class InactivityService {
    public static final String REMINDER = "🤖 ¿Sigues ahí? Estoy esperando tu respuesta...";
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final OutboxRepository outbox;
    private final OutboxPayloadBuilder payloadBuilder;
    private final TransactionalOperator tx;
    private final Clock clock;

    public InactivityService(ConversationRepository conversations, MessageRepository messages,
                             OutboxRepository outbox, OutboxPayloadBuilder payloadBuilder,
                             TransactionalOperator tx, Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.outbox = outbox;
        this.payloadBuilder = payloadBuilder;
        this.tx = tx;
        this.clock = clock;
    }

    public Mono<Void> processDue() {
        return Mono.defer(() -> {
            Instant now = clock.instant();
            // Meta solo permite mensajes libres dentro de las 24 h del último entrante.
            return tx.execute(status -> conversations.findReminderDueForUpdate(
                            now.minus(Duration.ofMinutes(15)), now.minus(Duration.ofHours(24)), 100)
                    .concatMap(conversation -> enqueue(conversation, now))).then();
        });
    }

    private Mono<Void> enqueue(Conversation conversation, Instant now) {
        Message message = Message.outboundPending(conversation.id(), REMINDER);
        String payload = payloadBuilder.build(EngineResult.textOnly(REMINDER, conversation.state()));
        Conversation updated = conversation.awaitingReply(message.id(),
                conversation.lastPromptPayload(), false, now);
        return conversations.update(updated)
                .then(messages.save(message))
                .then(outbox.save(OutboxMessage.pendingFor(message.id(), conversation.id(),
                        conversation.waId(), payload, now))).then();
    }
}
