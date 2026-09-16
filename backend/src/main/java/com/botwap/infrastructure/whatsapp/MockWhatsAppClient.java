package com.botwap.infrastructure.whatsapp;

import com.botwap.domain.model.WhatsAppSendResult;
import com.botwap.domain.port.WhatsAppClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Adaptador de simulación para entornos de desarrollo y tests.
 *
 * <p>Permite ejecutar el sistema completo SIN credenciales reales de Meta:
 * el flujo E2E (webhook → motor → outbox → poller → envío) es idéntico al de
 * producción; solo cambia el transporte.</p>
 *
 * <p>Fase 6: se agrega comportamiento configurable para pruebas de retry,
 * backoff y fallo permanente. Por defecto siempre devuelve éxito.</p>
 */
@Component
@ConditionalOnProperty(prefix = "whatsapp.client", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockWhatsAppClient implements WhatsAppClient {

    private static final Logger log = LoggerFactory.getLogger(MockWhatsAppClient.class);

    private final AtomicInteger callCount = new AtomicInteger(0);
    private volatile Predicate<Integer> shouldFail = attempt -> false;
    private volatile String failureMessage = "Simulated failure from MockWhatsAppClient";

    @Override
    public Mono<WhatsAppSendResult> sendMessage(String waId, String payloadJson) {
        return Mono.fromSupplier(() -> {
            int attempt = callCount.incrementAndGet();
            log.info("[mock-whatsapp] intento #{} a {} (sin contenido en logs)", attempt, waId);

            if (shouldFail.test(attempt)) {
                log.warn("[mock-whatsapp] simulando fallo en intento #{}", attempt);
                throw new RuntimeException(failureMessage);
            }

            return new WhatsAppSendResult("mock-wamid-" + UUID.randomUUID());
        });
    }

    /**
     * Configura el mock para que falle las primeras {@code failCount} veces
     * y luego devuelva éxito.
     */
    public void configureFailThenSucceed(int failCount) {
        callCount.set(0);
        shouldFail = attempt -> attempt <= failCount;
    }

    /** Configura el mock para que siempre falle. */
    public void configureAlwaysFail() {
        callCount.set(0);
        shouldFail = attempt -> true;
    }

    /** Configura el mock para que siempre tenga éxito (comportamiento por defecto). */
    public void configureAlwaysSucceed() {
        callCount.set(0);
        shouldFail = attempt -> false;
    }

    /** Establece el mensaje de error simulado. */
    public void setFailureMessage(String message) {
        this.failureMessage = message;
    }

    /** Devuelve el número de llamadas realizadas (para verificación en tests). */
    public int getCallCount() {
        return callCount.get();
    }

    /** Reinicia el contador y comportamiento a valores por defecto. */
    public void reset() {
        callCount.set(0);
        shouldFail = attempt -> false;
        failureMessage = "Simulated failure from MockWhatsAppClient";
    }
}