package com.botwap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración externa de WhatsApp (Webhook y Graph API de Meta).
 *
 * <p>Todos los secretos se inyectan mediante variables de entorno
 * ({@code WHATSAPP_*}); nunca se hardcodean valores reales.</p>
 */
@ConfigurationProperties(prefix = "whatsapp")
public record WhatsAppProperties(
        Client client,
        Webhook webhook,
        Api api) {

    /**
     * Selección del adaptador de envío: {@code mock} (desarrollo/tests) o {@code meta}.
     */
    public record Client(String mode) {
    }

    /**
     * Verificación del webhook ante Meta (hub.verify_token).
     */
    public record Webhook(String verifyToken) {
    }

    /**
     * Datos de conexión con la WhatsApp Cloud API (Graph API de Meta).
     *
     * @param appSecret     App Secret de Meta (firma HMAC del webhook)
     * @param accessToken   token Bearer de la Graph API (NUNCA se registra en logs)
     * @param phoneNumberId ID del número emisor ({@code POST .../{id}/messages})
     * @param wabaId        ID de la cuenta WABA
     * @param apiVersion    versión de la Graph API (p. ej. {@code v21.0})
     * @param baseUrl       host de la Graph API (configurable para sandbox/stub)
     * @param timeoutMs     timeout de respuesta HTTP en milisegundos (Fase 7A)
     * @param connectTimeoutMs timeout de establecimiento de conexión en milisegundos (Fase 7A)
     */
    public record Api(String appSecret,
                      String accessToken,
                      String phoneNumberId,
                      String wabaId,
                      String apiVersion,
                      String baseUrl,
                      @DefaultValue("10000") long timeoutMs,
                      @DefaultValue("5000") long connectTimeoutMs) {
    }
}