package com.botwap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
     */
    public record Api(String appSecret,
                      String accessToken,
                      String phoneNumberId,
                      String wabaId,
                      String apiVersion,
                      String baseUrl) {
    }
}