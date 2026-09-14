package com.botwap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración externalizada del OutboxPoller.
 *
 * <p>Controla el comportamiento de la cola de salida: intervalo de sondeo,
 * duración del lease, reintentos máximos y base del backoff exponencial.
 * Todas las propiedades se inyectan mediante variables de entorno
 * ({@code APP_OUTBOX_*}) o archivos de configuración.</p>
 */
@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(
        boolean enabled,
        long pollIntervalMs,
        long leaseDurationSeconds,
        int maxAttempts,
        long backoffBaseSeconds) {

    /**
     * Calcula el instante del siguiente intento usando backoff exponencial.
     *
     * <p>Fórmula: {@code baseDelay * 2^(attempt - 1)} segundos.
     * Ejemplo con base=1: attempt 1 → 1s, attempt 2 → 2s, attempt 3 → 4s.
     * Se limita a 2^20 (~12 días) para evitar overflow.</p>
     *
     * @param attempt número de intento actual (1-based)
     * @return segundos de espera hasta el siguiente intento
     */
    public long backoffDelaySeconds(int attempt) {
        if (attempt <= 0) {
            return backoffBaseSeconds;
        }
        // Limitar el exponente para evitar overflow absurdos
        int exponent = Math.min(attempt - 1, 20);
        return backoffBaseSeconds * (1L << exponent);
    }
}
