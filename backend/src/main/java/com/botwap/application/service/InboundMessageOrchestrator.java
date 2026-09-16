package com.botwap.application.service;

import com.botwap.domain.engine.BotCopy;
import com.botwap.domain.engine.ConversationEngine;
import com.botwap.domain.engine.EngineRequest;
import com.botwap.domain.engine.EngineResult;
import com.botwap.domain.engine.InputNormalizer;
import com.botwap.domain.menu.MenuCatalog;
import com.botwap.domain.model.Conversation;
import com.botwap.domain.model.ConversationSelection;
import com.botwap.domain.model.ConversationState;
import com.botwap.domain.model.ConversationStatus;
import com.botwap.domain.model.Message;
import com.botwap.domain.model.MessageType;
import com.botwap.domain.model.OutboxMessage;
import com.botwap.domain.port.AiAssistant;
import com.botwap.domain.port.ConversationRepository;
import com.botwap.domain.port.ConversationSelectionRepository;
import com.botwap.domain.port.MessageRepository;
import com.botwap.domain.port.OutboxRepository;
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
    private final OutboxPayloadBuilder payloadBuilder;
    private final AiAssistant aiAssistant;

    /**
     * Número del asesor/administrador que recibe la alerta de handoff humano.
     *
     * <p>Etapa temprana: constante inmutable (sin variable de entorno). El motor
     * genera un segundo {@code OutboxMessage} dirigido a este número cuando la
     * conversación transiciona a {@code HUMAN_AGENT}.</p>
     */
    public static final String ADMIN_PHONE = "573234198831";

    public InboundMessageOrchestrator(ConversationEngine engine,
                                      ConversationRepository conversationRepository,
                                      ConversationSelectionRepository selectionRepository,
                                      MessageRepository messageRepository,
                                      OutboxRepository outboxRepository,
                                      TransactionalOperator tx,
                                      OutboxPayloadBuilder payloadBuilder,
                                      AiAssistant aiAssistant) {
        this.engine = engine;
        this.conversationRepository = conversationRepository;
        this.selectionRepository = selectionRepository;
        this.messageRepository = messageRepository;
        this.outboxRepository = outboxRepository;
        this.tx = tx;
        this.payloadBuilder = payloadBuilder;
        this.aiAssistant = aiAssistant;
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
    public Mono<Void> processInbound(String waId, String wamid, String text, String profileName) {
        log.warn("DIAG processInbound: waId={} wamid={} text='{}'", waId, wamid, text);
        return messageRepository.existsByWaMessageId(wamid)
                .flatMap(exists -> {
                    if (exists) {
                        log.info("Mensaje duplicado ignorado: wamid={}", wamid);
                        return Mono.<Void>empty();
                    }
                    return processNewInbound(waId, wamid, text, profileName);
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

    private Mono<Void> processNewInbound(String waId, String wamid, String text, String profileName) {
        return tx.execute(status -> resolveConversation(waId, wamid, text, profileName)
                .flatMap(conversation -> processWithConversation(conversation, waId, wamid, text, profileName))
                .then()).then();
    }

    /**
     * Resuelve la conversación sobre la que se procesará el mensaje.
     *
     * <p>Si no hay conversación ACTIVA:
     * <ul>
     *   <li>Se busca la última conversación del usuario (cerrada o no).</li>
     *   <li>Si está CERRADA y el mensaje reinicia explícitamente ({@code hola}/
     *       {@code menu}) se abre una nueva conversación en MAIN_MENU.</li>
     *   <li>Si está CERRADA y el mensaje NO reinicia, se registra el entrante y se
     *       devuelve vacío: no hay respuesta. Así el estado final queda cerrado e
     *       inactivo y no se disparan mensajes automáticos.</li>
     *   <li>Si no existe ninguna, es el primer contacto: se crea la conversación.</li>
     *   <li>{@code HUMAN_AGENT} nunca se cierra: el despertador ({@code INICIO}/{@code MENU})
     *       regresa a {@code MAIN_MENU} dentro de la misma conversación activa.</li>
     * </ul>
     */
    private Mono<Conversation> resolveConversation(String waId, String wamid, String text, String profileName) {
        return conversationRepository.findActiveByWaIdForUpdate(waId)
                .switchIfEmpty(Mono.defer(() -> conversationRepository.findLatestByWaIdForUpdate(waId)
                        .switchIfEmpty(Mono.defer(() -> insertNewConversation(waId, profileName)))
                        .flatMap(latest -> {
                            if (!latest.isClosed()) {
                                // Defensivo: conversación no cerrada que no pasó el
                                // filtro de "ACTIVE" (no debería ocurrir).
                                return Mono.just(latest);
                            }
                            if (!InputNormalizer.isRestartCommand(InputNormalizer.normalize(text))) {
                                log.info("Conversacion terminal: mensaje registrado sin respuesta. waId={}", waId);
                                return persistInboundOnly(latest, wamid, text).then(Mono.empty());
                            }
                            return insertNewConversation(waId, profileName);
                        })));
    }

    private Mono<Conversation> insertNewConversation(String waId, String profileName) {
        return conversationRepository.insert(Conversation.newActive(waId, profileName));
    }

    /**
     * Ejecuta el motor y persiste el resultado.
     *
     * <p>Fase V2 (Enterprise):
     * <ul>
     *   <li>Actualiza {@code profile_name} si el webhook trae un nombre nuevo.</li>
     *   <li>Despertador {@code HUMAN_AGENT}: {@code INICIO}/{@code MENU} regresa a
     *       {@code MAIN_MENU} con el menú interactivo principal.</li>
     *   <li>Texto libre (no coincide con opción del menú ni comando) se deriva a
     *       Gemini; si clasifica {@code AGENT} o el motor ya transicionó a
     *       {@code HUMAN_AGENT}, se genera el doble mensaje (cliente + admin).</li>
     *   <li>{@code HUMAN_AGENT} permanece {@code ACTIVE} (no se cierra) para que el
     *       silencio sea permanente hasta el despertador.</li>
     * </ul>
     * </p>
     */
    private Mono<Void> processWithConversation(Conversation conversation, String waId,
                                               String wamid, String text, String profileName) {
        Conversation withProfile = refreshProfileName(conversation, profileName);
        if (conversation.state() == ConversationState.HUMAN_AGENT
                && isWakeWord(text)) {
            return selectionRepository.findByConversationId(conversation.id()).collectList()
                    .flatMap(selections -> {
                        EngineResult wake = EngineResult.menu(BotCopy.backToMain(),
                                ConversationState.MAIN_MENU, null,
                                com.botwap.domain.menu.InteractiveOption.listOf(MenuCatalog.mainMenu()));
                        Message inbound = Message.inbound(withProfile.id(), wamid, text);
                        Conversation updated = withProfile.withState(ConversationState.MAIN_MENU);
                        return persistEngineResult(withProfile, updated, inbound, wake, waId);
                    });
        }
        return selectionRepository.findByConversationId(conversation.id()).collectList()
                .flatMap(selections -> {
                    String effectiveName = withProfile.profileName() != null
                            ? withProfile.profileName() : profileName;
                    EngineRequest request = new EngineRequest(
                            withProfile.id(), waId, text, withProfile.state(), effectiveName, selections);
                    EngineResult result = engine.process(request, withProfile);

                    if (result.nextState() == ConversationState.HUMAN_AGENT && result.hasResponse()) {
                        Message inbound = Message.inbound(withProfile.id(), wamid, text);
                        Conversation updated = withProfile.withState(ConversationState.HUMAN_AGENT);
                        return persistHandoff(withProfile, updated, inbound, result, waId);
                    }

                    if (!result.hasResponse()) {
                        return persistInboundOnly(withProfile, wamid, text);
                    }

                    if (isFreeTextFallback(result, text)) {
                        return resolveWithAi(withProfile, waId, wamid, text, selections, result);
                    }

                    Message inbound = Message.inbound(withProfile.id(), wamid, text);
                    Conversation updated = withProfile.withState(result.nextState());
                    if (result.nextState().isTerminal()) {
                        updated = updated.withStatus(ConversationStatus.CLOSED);
                    }

                    return persistEngineResult(withProfile, updated, inbound, result, waId);
                });
    }

    /** Actualiza el nombre de perfil si el webhook trae uno nuevo y distinto. */
    private Conversation refreshProfileName(Conversation conversation, String profileName) {
        String normalized = Conversation.normalizeProfileName(profileName);
        if (normalized == null || normalized.equals(conversation.profileName())) {
            return conversation;
        }
        return conversation.withProfileName(normalized);
    }

    /** Despertador del handoff: {@code INICIO} o {@code MENU} (case/space-insensitive). */
    public static boolean isWakeWord(String text) {
        if (text == null) {
            return false;
        }
        String normalized = InputNormalizer.normalize(text);
        return "inicio".equals(normalized) || "menu".equals(normalized)
                || "/menu".equals(normalized) || "/start".equals(normalized);
    }

    /**
     * Texto libre: el motor devolvió re-prompt sin cambiar de estado ante una entrada
     * que no es comando global, ni saludo, ni opción válida del menú, ni número.
     */
    private boolean isFreeTextFallback(EngineResult result, String text) {
        if (result.nextState() == null) {
            return false;
        }
        String normalized = InputNormalizer.normalize(text);
        if (normalized.isBlank() || InputNormalizer.isGlobalCommand(normalized)
                || InputNormalizer.isHello(normalized)) {
            return false;
        }
        if (InputNormalizer.asNumber(normalized).isPresent()) {
            return false;
        }
        // El re-prompt de "no entendido" puede ir acompañado del menú nativo de
        // re-envío (Outcome.menu con options). Si el texto de respuesta es exactamente
        // el fallback de BotCopy.notUnderstood(), el usuario envió texto libre que
        // debe derivarse a Gemini — independientemente de que un menú se reenvíe.
        if (BotCopy.notUnderstood().equals(result.responseText())) {
            return true;
        }
        if (result.hasMenu() || result.selection() != null) {
            return false;
        }
        return !result.hasResponse();
    }

    /**
     * Deriva el texto libre a Gemini (fail-open): {@code AGENT} → handoff doble;
     * respuesta útil → texto de la IA; fallo → re-prompt del motor.
     */
    private Mono<Void> resolveWithAi(Conversation conversation, String waId, String wamid,
                                     String text, java.util.List<ConversationSelection> selections,
                                     EngineResult fallback) {
        if (aiAssistant == null || !aiAssistant.isEnabled()) {
            return persistFallback(conversation, waId, wamid, text, fallback);
        }
        java.util.List<String> menuOptions = MenuCatalog.optionsFor(conversation.state().name())
                .stream().map(o -> o.optionKey() + ": " + o.label()).toList();
        AiAssistant.AiRequest aiRequest = new AiAssistant.AiRequest(
                conversation.profileName(), conversation.state().name(), menuOptions, text);
        return aiAssistant.assist(aiRequest)
                .map(reply -> toAiResult(conversation, reply, fallback))
                .onErrorResume(e -> {
                    log.warn("Gemini no disponible, se usa el motor local: {}", e.toString());
                    return Mono.just(fallback);
                })
                .flatMap(aiResult -> {
                    if (aiResult.nextState() == ConversationState.HUMAN_AGENT) {
                        Message inbound = Message.inbound(conversation.id(), wamid, text);
                        Conversation updated = conversation.withState(ConversationState.HUMAN_AGENT);
                        return persistHandoff(conversation, updated, inbound, aiResult, waId);
                    }
                    if (!aiResult.hasResponse()) {
                        return persistInboundOnly(conversation, wamid, text);
                    }
                    Message inbound = Message.inbound(conversation.id(), wamid, text);
                    Conversation updated = conversation.withState(aiResult.nextState());
                    if (aiResult.nextState().isTerminal()) {
                        updated = updated.withStatus(ConversationStatus.CLOSED);
                    }
                    return persistEngineResult(conversation, updated, inbound, aiResult, waId);
                });
    }

    /** Traduce la respuesta de Gemini a {@code EngineResult} (con intención AGENT). */
    private EngineResult toAiResult(Conversation conversation, AiAssistant.AiReply reply,
                                    EngineResult fallback) {
        if (reply == null || "AGENT".equalsIgnoreCase(reply.intent())) {
            return EngineResult.textOnly(BotCopy.humanHandoff(), ConversationState.HUMAN_AGENT);
        }
        if (reply.hasReply()) {
            java.util.List<com.botwap.domain.menu.InteractiveOption> options =
                    MenuCatalog.optionsFor(conversation.state().name()).isEmpty() ? java.util.List.of()
                            : com.botwap.domain.menu.InteractiveOption.listOf(
                                    MenuCatalog.optionsFor(conversation.state().name()));
            if (options.isEmpty()) {
                return EngineResult.textOnly(reply.reply(), conversation.state());
            }
            return EngineResult.menu(reply.reply(), conversation.state(), null, options);
        }
        return fallback;
    }

    /** Persiste el re-prompt del motor cuando la IA está deshabilitada o falla. */
    private Mono<Void> persistFallback(Conversation conversation, String waId, String wamid,
                                       String text, EngineResult fallback) {
        if (!fallback.hasResponse()) {
            return persistInboundOnly(conversation, wamid, text);
        }
        Message inbound = Message.inbound(conversation.id(), wamid, text);
        Conversation updated = conversation.withState(fallback.nextState());
        if (fallback.nextState().isTerminal()) {
            updated = updated.withStatus(ConversationStatus.CLOSED);
        }
        return persistEngineResult(conversation, updated, inbound, fallback, waId);
    }

    /** Persiste el resultado del motor (texto o menú interactivo nativo). */
    private Mono<Void> persistEngineResult(Conversation base, Conversation updated,
                                           Message inbound, EngineResult result, String waId) {
        Message outbound = Message.outboundPending(updated.id(), result.responseText(),
                result.hasMenu() ? MessageType.INTERACTIVE : MessageType.TEXT);
        return persistAll(base, updated, inbound, outbound, result, waId);
    }

    /**
     * Handoff humano (Fase V2 — actualización Fase 4): doble mensaje.
     *
     * <ol>
     *   <li>Cliente: aviso de transferencia + nota {@code INICIO} para cancelar la espera.</li>
     *   <li>Admin ({@link #ADMIN_PHONE}): alerta con nombre y Wa ID del usuario.</li>
     * </ol>
     *
     * <p>La conversación permanece {@code ACTIVE} en {@code HUMAN_AGENT}: el bot queda en
     * silencio permanente hasta el despertador.</p>
     */
    private Mono<Void> persistHandoff(Conversation base, Conversation updated,
                                      Message inbound, EngineResult result, String waId) {
        Message clientOutbound = Message.outboundPending(updated.id(), result.responseText(),
                MessageType.TEXT);
        String displayName = updated.profileName() != null ? updated.profileName() : waId;
        Message adminOutbound = Message.outboundPending(updated.id(),
                BotCopy.adminAlert(displayName, waId), MessageType.TEXT);
        return persistHandoffAll(base, updated, inbound, clientOutbound, adminOutbound, result.selection());
    }

    /** Persiste el handoff: conversación + entrante + 2 salientes + 2 outbox + selección. */
    private Mono<Void> persistHandoffAll(Conversation base, Conversation updated,
                                         Message inbound, Message clientOutbound, Message adminOutbound,
                                         ConversationSelection selection) {
        Mono<Void> saveConversation = conversationRepository.update(updated).then();
        Mono<Void> saveInbound = messageRepository.save(inbound).then();
        Mono<Void> saveClient = messageRepository.save(clientOutbound).then();
        Mono<Void> saveAdmin = messageRepository.save(adminOutbound).then();
        Mono<Void> saveSelection = selection != null
                ? selectionRepository.save(selection.withConversationId(updated.id())
                        .withSelectedAt(java.time.Instant.now())).then()
                : Mono.empty();
        OutboxMessage clientBox = OutboxMessage.pendingFor(clientOutbound.id(), updated.id(),
                base.waId(), payloadBuilder.build(EngineResult.textOnly(clientOutbound.content(),
                        updated.state())),
                java.time.Instant.now());
        OutboxMessage adminBox = OutboxMessage.pendingFor(adminOutbound.id(), updated.id(),
                ADMIN_PHONE, payloadBuilder.build(
                        EngineResult.textOnly(adminOutbound.content(), updated.state())),
                java.time.Instant.now());
        return saveConversation
                .then(saveInbound)
                .then(saveClient)
                .then(saveAdmin)
                .then(saveSelection)
                .then(outboxRepository.save(clientBox)).then()
                .then(outboxRepository.save(adminBox)).then()
                .then(messageRepository.markReceivedAsProcessed(inbound.id()))
                .then();
    }

    /** Persiste el mensaje entrante y lo marca PROCESSED, sin generar saliente. */
    private Mono<Void> persistInboundOnly(Conversation conversation, String wamid, String text) {
        Message inbound = Message.inbound(conversation.id(), wamid, text);
        return messageRepository.save(inbound)
                .then(messageRepository.markReceivedAsProcessed(inbound.id()))
                .then();
    }

    /**
     * Persiste conversación + entrante + saliente + selección + outbox (texto o interactivo).
     *
     * <p>Secuencial (no Mono.zip): evita carreras entre el INSERT del outbox
     * (FK -> message.id) y el INSERT del outbound, y da un orden deterministico.
     * Estrictamente fail-fast: SIN onErrorResume aqui dentro.</p>
     */
    private Mono<Void> persistAll(Conversation base, Conversation updated,
                                  Message inbound, Message outbound,
                                  EngineResult result, String waId) {
        Mono<Void> saveConversation = conversationRepository.update(updated).then();
        Mono<Void> saveInbound = messageRepository.save(inbound).then();
        Mono<Void> saveOutbound = messageRepository.save(outbound).then();
        Mono<Void> saveSelection = result.selection() != null
                ? selectionRepository.save(result.selection().withConversationId(updated.id())
                        .withSelectedAt(Instant.now())).then()
                : Mono.empty();
        String payload = payloadBuilder.build(result);
        OutboxMessage outbox = OutboxMessage.pendingFor(outbound.id(), updated.id(), waId,
                payload, Instant.now());
        return saveConversation
                .then(saveInbound)
                .then(saveOutbound)
                .then(saveSelection)
                .then(outboxRepository.save(outbox)).then()
                .then(messageRepository.markReceivedAsProcessed(inbound.id()))
                .then();
    }


}
