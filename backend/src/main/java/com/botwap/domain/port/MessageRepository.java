package com.botwap.domain.port;

import com.botwap.domain.model.Message;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Puerto de persistencia de mensajes (entrantes y salientes).
 *
 * <p>El método {@link #markReceivedAsProcessed} materializa la transición
 * idempotente {@code RECEIVED → PROCESSED} que garantiza que un mensaje se
 * procese exactamente una vez (docs/DATABASE_DESIGN.md § 3.1).</p>
 */
public interface MessageRepository {

    Mono<Message> save(Message message);

    Mono<Message> findById(UUID id);

    Mono<Boolean> existsByWaMessageId(String waMessageId);

    /** Si afecta 0 filas, el mensaje ya fue procesado (deduplicación). */
    Mono<Boolean> markReceivedAsProcessed(UUID messageId, String waMessageId);
}