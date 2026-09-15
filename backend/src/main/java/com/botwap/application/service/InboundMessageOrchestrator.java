package com.botwap.application.service;

import com.botwap.domain.engine.ConversationEngine;
import com.botwap.domain.engine.EngineRequest;
import com.botwap.domain.engine.EngineResult;
import com.botwap.domain.model.Conversation;
import com.botwap.domain.model.ConversationSelection;
import com.botwap.domain.model.ConversationState;
import com.botwap.domain.model.ConversationStatus;
import com.botwap.domain.model.Message;
import com.botwap.domain.model.OutboxMessage;
import com.botwap.domain.port.ConversationRepository;
import com.botwap.domain.port.ConversationSelectionRepository;
import com.botwap.domain.port.MessageRepository;
import com.botwap.domain.port.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import com.botwap.application.exception.ConcurrencyConflictException;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
public class InboundMessageOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(InboundMessageOrchestrator.class);

    private final ConversationEngine engine;
    private final ConversationRepository conversationRepository;
    private final ConversationSelectionRepository selectionRepository;
    private final MessageRepository messageRepository;
    private final OutboxRepository outboxRepository;
    private final TransactionalOperator tx;
    private final ObjectMapper objectMapper;

    public InboundMessageOrchestrator(ConversationEngine engine,
                                      ConversationRepository conversationRepository,
                                      ConversationSelectionRepository selectionRepository,
                                      MessageRepository messageRepository,
                                      OutboxRepository outboxRepository,
                                      TransactionalOperator tx,
                                      ObjectMapper objectMapper) {
        this.engine = engine;
        this.conversationRepository = conversationRepository;
        this.selectionRepository = selectionRepository;
        this.messageRepository = messageRepository;
        this.outboxRepository = outboxRepository;
        this.tx = tx;
        this.objectMapper = objectMapper;
    }

    /**
     * Fase A: ingestacion, deduplicacion, transaccion de procesamiento y
     * encolado de la respuesta en el Outbox.
     *
     * <p>La deduplicacion se apoya en dos defensas complementarias:
     * <ul>
     *   <li>Lectura optimista previa de {@code wa_message_id} para rechazar
     *       reintentos rapidos del webhook sin tocar la transaccion.</li>
     *   <li>Captura de {@link DataIntegrityViolationException} FUERA de la
     *       transaccion como red de seguridad ante condiciones de carrera: si
     *       dos hilos ingresan el mismo {@code wamid} casi al mismo tiempo,
     *       solo uno gana el INSERT y el otro ve la excepcion de constraint,
     *       que interpretamos como "duplicado ya persistido" y se convierte en
     *       exito silencioso (idempotencia garantizada por la clave unica en
     *       base de datos, no por memoria).</li>
     * </ul>
     *
     * <p><b>Regla critica R2DBC/Postgres:</b> NUNCA se captura (swallow) un
     * error DENTRO de {@code tx.execute(...)}. En Postgres, cualquier sentencia
     * fallida aborta la transaccion completa; tragar el error y continuar deja
     * la transaccion en estado ABORTED y el commit final falla con
     * {@code "The database returned ROLLBACK"} (mapeado por Spring a
     * {@code PessimisticLockingFailureException}), lo que rompia el webhook con
     * HTTP 500 hacia Meta. Por eso todo el bloque transaccional es estricto
     * (fail-fast): cualquier fallo aborta y hace rollback limpio, y la
     * clasificacion benigno vs. error se hace FUERA, sobre el Mono ya
     * transaccionado.</p>
     */
    public Mono<Void> processInbound(String waId, String wamid, String text) {
        log.warn("DIAG processInbound: waId={} wamid={} text='{}'", waId, wamid, text);
        return messageRepository.existsByWaMessageId(wamid)
                .flatMap(exists -> {
                    if (exists) {
                        log.info("Mensaje duplicado ignorado: wamid={}", wamid);
                        return Mono.<Void>empty();
                    }
                    return processNewInbound(waId, wamid, text);
                })
                .onErrorResume(DataIntegrityViolationException.class,
                        e -> {
                            // Condicion de carrera: el wamid ya fue insertado por otro hilo.
                            // Ocurre FUERA de tx (o como rollback limpio de tx): es benigno.
                            log.info("Mensaje duplicado por carrera de concurrencia: wamid={} {}", wamid, e.getMessage());
                            return Mono.empty();
                        })
                .onErrorResume(ConcurrencyConflictException.class,
                        e -> {
                            // Optimistic-lock perdido (dos webhooks concurrentes del mismo
                            // wa_id). Meta reintentara; el reintento leera el estado nuevo.
                            // Se considera benigno: ack para no provocar retry-storm.
                            log.info("Conflicto de concurrencia procesando wamid={}: {}", wamid, e.getMessage());
                            return Mono.empty();
                        })
                .onErrorResume(PessimisticLockingFailureException.class,
                        e -> {
                            // Commit sobre tx abortada / lock concurrente. Ya hubo rollback
                            // limpio en Postgres; no hay estado parcial. Se traga aqui
                            // (FUERA de tx) para que el controlador pueda responder 200.
                            log.warn("Fallo de commit/lock procesando wamid={} (rollback limpio, sin estado parcial): {}",
                                    wamid, e.getMessage());
                            return Mono.empty();
                        })
                .onErrorResume(TransientDataAccessResourceException.class,
                        e -> {
                            log.warn("Fallo transitorio de BD procesando wamid={}: {}", wamid, e.getMessage());
                            return Mono.empty();
                        });
    }

    private Mono<Void> processNewInbound(String waId, String wamid, String text) {
        return tx.execute(status -> {
            Mono<Conversation> convMono = conversationRepository.findActiveByWaIdForUpdate(waId)
                    .switchIfEmpty(Mono.defer(() -> {
                        Conversation newConv = Conversation.newActive(waId);
                        return conversationRepository.insert(newConv);
                    }));

            return convMono.flatMap(conversation ->
                    selectionRepository.findByConversationId(conversation.id()).collectList()
                            .flatMap(selections -> {
                                EngineRequest request = new EngineRequest(
                                        conversation.id(), waId, text, conversation.state(), selections);
                                EngineResult result = engine.process(request, conversation);

                                Message inbound = Message.inbound(conversation.id(), wamid, text);
                                Message outbound = Message.outboundPending(conversation.id(), result.responseText());

                                Conversation updated = conversation.withState(result.nextState());
                                if (result.nextState() == ConversationState.FINAL
                                        || result.nextState() == ConversationState.CANCELLED) {
                                    updated = updated.withStatus(ConversationStatus.CLOSED);
                                }

                                return persistAll(waId, inbound, outbound, updated, result.selection());
                            }));
        }).then();
    }

    private Mono<Void> persistAll(String waId, Message inbound, Message outbound, Conversation updated,
                                  ConversationSelection selection) {
        Mono<Void> saveConversation = conversationRepository.update(updated).then();
        Mono<Void> saveInbound = messageRepository.save(inbound).then();
        Mono<Void> saveOutbound = messageRepository.save(outbound).then();
        Mono<Void> saveSelection = selection != null
                ? selectionRepository.save(selection.withConversationId(updated.id())
                        .withSelectedAt(Instant.now())).then()
                : Mono.empty();

        Mono<OutboxMessage> outboxMono = buildOutbox(outbound, waId);

        // Secuencial (no Mono.zip): evita carreras entre el INSERT del outbox
        // (FK -> message.id) y el INSERT del outbound, y da un orden deterministico.
        // Estrictamente fail-fast: SIN onErrorResume aqui dentro. Tragar errores
        // dentro de tx deja a Postgres en ABORTED y el commit falla con
        // "The database returned ROLLBACK" -> HTTP 500 a Meta.
        return saveConversation
                .then(saveInbound)
                .then(saveOutbound)
                .then(saveSelection)
                .then(outboxMono.flatMap(outboxRepository::save))
                .then(messageRepository.markReceivedAsProcessed(inbound.id()))
                .then();
    }

    private Mono<OutboxMessage> buildOutbox(Message outbound, String waId) {
        String content = outbound.content() == null ? "" : outbound.content();
        String payload = serializePayload(content);
        return Mono.just(OutboxMessage.pendingFor(
                outbound.id(),
                outbound.conversationId(),
                waId,
                payload,
                Instant.now()));
    }

    /**
     * Serializa el payload del mensaje saliente a JSON de forma segura usando
     * Jackson (evita JSON invalido: comillas, saltos de linea reales, etc.).
     */
    private String serializePayload(String text) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of("text", text));
        } catch (JsonProcessingException e) {
            log.error("No se pudo serializar el payload del outbox", e);
            return "{\"text\":\"\"}";
        }
    }
}
