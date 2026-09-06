package com.botwap.infrastructure.web;

import com.botwap.config.WhatsAppProperties;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Verificador de la firma {@code X-Hub-Signature-256} que Meta adjunta a cada
 * POST del webhook (HMAC-SHA256 del body crudo con el app secret).
 *
 * <p><strong>Esqueleto declarado en FASE 2.</strong> La implementación completa
 * (HMAC-SHA256 + comparación en tiempo constante con
 * {@code MessageDigest.isEqual}) corresponde a la Fase 4 (sección de seguridad
 * del webhook, docs/WHATSAPP_INTEGRATION.md § 5).</p>
 */
@Component
public class WebhookSignatureVerifier {

    private final WhatsAppProperties properties;

    public WebhookSignatureVerifier(WhatsAppProperties properties) {
        this.properties = properties;
    }

    /**
     * Verifica que la firma del request corresponda al body recibido.
     */
    public Mono<Boolean> verify(String signatureHeader, String rawBody) {
        throw new UnsupportedOperationException(
                "TODO FASE 4: verificación HMAC-SHA256 con whatsapp.api.app-secret.");
    }
}