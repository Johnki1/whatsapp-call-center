package com.botwap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración externa del asistente de IA (Google Gemini).
 *
 * <p>La API key se inyecta por variable de entorno ({@code GEMINI_API_KEY},
 * ya configurada en el Key Vault del servidor) y NUNCA se registra en logs.
 * Si {@code enabled=false} o la key está vacía, el orquestador degrada a la
 * máquina de estados pura (coste operativo $0 cuando no se usa).</p>
 *
 * @param enabled   interruptor del asistente ({@code APP_GEMINI_ENABLED})
 * @param apiKey    API key de Google AI Studio ({@code GEMINI_API_KEY})
 * @param model     modelo a invocar ({@code GEMINI_MODEL}; por defecto
 *                  {@code gemini-3.5-flash-lite}, la variante más económica)
 * @param baseUrl   host de la Generative Language API (configurable para stubs)
 * @param timeoutMs timeout de respuesta HTTP en milisegundos
 */
@ConfigurationProperties(prefix = "app.gemini")
public record GeminiProperties(
        @DefaultValue("true") boolean enabled,
        String apiKey,
        @DefaultValue("gemini-3.5-flash-lite") String model,
        @DefaultValue("https://generativelanguage.googleapis.com") String baseUrl,
        @DefaultValue("8000") long timeoutMs) {

    /** Indica si hay configuración suficiente para invocar al modelo. */
    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
