package com.botwap.application.service;

import com.botwap.config.OutboxProperties;
import com.botwap.domain.model.OutboxMessage;
import com.botwap.domain.model.WhatsAppSendResult;
import com.botwap.domain.port.OutboxRepository;
import com.botwap.domain.port.WhatsAppClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Componente de polling del Outbox (Fase 6).
 *
 * <p>Responsabilidades:
 * <ol>
 *   <li>Buscar mensajes PENDING elegibles (con backoff respetado).</li>
 *   <li>Reclamarlos atómicamente (PENDING → SENDING con lease).</li>
 *   <li>Enviar el payload (texto o interactivo) vía {@link WhatsAppClient}.</li>
 *   <li>Marcar SENT en éxito, o PENDING/FAILED en error según reintentos.</li>
 * </ol>
 *
 * <p>El control de concurrencia reside en PostgreSQL ({@code FOR UPDATE SKIP LOCKED}):
 * no hay locks en memoria ni estado compartido. El lease permite recuperar mensajes
 * bloqueados por un crash del worker.</p>
 *
 * <p>Semántica de entrega: <strong>at-least-once</strong>. En la ventana de crash
 * (proveedor aceptó el mensaje pero el proceso murió antes de marcar SENT), el mensaje
 * puede enviarse nuevamente cuando el lease expire.</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.outbox", name = "enabled", havingValue = "true", matchIfMissing = false)
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxRepository outboxRepository;
    private final WhatsAppClient whatsAppClient;
    private final OutboxProperties properties;

    public OutboxPoller(OutboxRepository outboxRepository,
                        WhatsAppClient whatsAppClient,
                        OutboxProperties properties) {
        this.outboxRepository = outboxRepository;
        this.whatsAppClient = whatsAppClient;
        this.properties = properties;
    }

    /**
     * Ejecuta un ciclo de procesamiento del Outbox.
     *
     * <p>Se invoca periódicamente mediante {@link Scheduled}. Cada ciclo:
     * <ol>
     *   <li>Reclama hasta {@code limit} mensajes PENDING elegibles.</li>
     *   <li>Los procesa secuencialmente (cada uno: envío + actualización).</li>
     * </ol>
     *
     * <p>El procesamiento es reactivo y no bloqueante. Los errores individuales
     * de un mensaje no afectan a los demás.</p>
     */
    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")
    public void poll() {
        processPending()
                .subscribe(
                        null,
                        error -> log.error("Error en ciclo del OutboxPoller: {}", error.getMessage())
                );
    }

    /**
     * Procesa los mensajes pendientes del Outbox.
     *
     * <p>Método público para permitir invocación directa en tests sin esperar
     * al scheduler.</p>
     *
     * @return Mono que completa cuando todos los mensajes reclamados fueron procesados
     */
    public Mono<Void> processPending() {
        return outboxRepository.claimPending(10, true)
                .flatMap(this::processOne, 1) // Concurrency 1 para evitar solapamiento
                .then();
    }

    /**
     * Procesa un único mensaje del Outbox.
     *
     * <p>Flujo:
     * <ol>
     *   <li>Extraer texto del payload JSON.</li>
     *   <li>Enviar vía WhatsAppClient.</li>
     *   <li>En éxito: marcar SENT.</li>
     *   <li>En error: si quedan intentos, programar retry con backoff; si no, marcar FAILED.</li>
     * </ol>
     */
    private Mono<Void> processOne(OutboxMessage message) {
        if (message.attempts() > properties.maxAttempts()) {
            // La recuperación por lease vencido (crash recovery) no acota intentos:
            // sin este tope, una fila que se reclama una y otra vez se reenviaría
            // indefinidamente (mensajes repetidos al usuario). Se falla de forma
            // explícita y sin volver a enviar.
            log.error("OutboxPoller: outboxId={} supera maxAttempts={} sin confirmar; se marca FAILED sin reenviar",
                    message.id(), properties.maxAttempts());
            return outboxRepository.markFailed(message.id(), Instant.now(),
                    "Supera el maximo de intentos sin confirmacion (recuperacion por lease)").then();
        }

        log.info("OutboxPoller: procesando outboxId={}, waId={}, attempt={}",
                message.id(), message.waId(), message.attempts());

        // El payload ya es el mensaje completo (texto o interactivo); el cliente
        // de WhatsApp lo traduce al cuerpo de la Graph API. Nunca se registra el
        // contenido en logs (datos personales del usuario).
        return whatsAppClient.sendMessage(message.waId(), message.payload())
                .flatMap(result -> {
                    log.info("OutboxPoller: envío exitoso outboxId={}, wamid={}",
                            message.id(), result.wamid());
                    return outboxRepository.markSent(message.id(), Instant.now());
                })
                .onErrorResume(error -> {
                    String errorMsg = error.getClass().getSimpleName() + ": " + error.getMessage();
                    log.warn("OutboxPoller: error en envío outboxId={}, attempt={}, error={}",
                            message.id(), message.attempts(), errorMsg);

                    int attempts = message.attempts();
                    if (attempts >= properties.maxAttempts()) {
                        // Agotó reintentos: marcar FAILED
                        return outboxRepository.markFailed(message.id(), Instant.now(), errorMsg);
                    } else {
                        // Programar retry con backoff exponencial
                        // attempts ya fue incrementado por claimPending, no duplicar
                        long delaySeconds = properties.backoffDelaySeconds(attempts);
                        Instant nextAttempt = Instant.now().plusSeconds(delaySeconds);
                        log.info("OutboxPoller: reintentando outboxId={} en {}s (attempt {})",
                                message.id(), delaySeconds, attempts);
                        return outboxRepository.scheduleRetry(
                                message.id(), attempts, nextAttempt, Instant.now(), errorMsg);
                    }
                })
                .then();
    }
}
