package com.botwap.infrastructure.web;

import com.botwap.config.WhatsAppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Verificador de la firma {@code X-Hub-Signature-256} que Meta adjunta a cada
 * POST del webhook (HMAC-SHA256 del body crudo con el app secret).
 *
 * <p>La comparación usa {@link java.security.MessageDigest#isEqual} (tiempo
 * constante) para evitar timing attacks (docs/WHATSAPP_INTEGRATION.md § 5).</p>
 */
@Component
public class WebhookSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(WebhookSignatureVerifier.class);

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SHA256_PREFIX = "sha256=";

    private final WhatsAppProperties properties;

    public WebhookSignatureVerifier(WhatsAppProperties properties) {
        this.properties = properties;
    }

    /**
     * Verifica que la firma del request corresponda al body recibido.
     *
     * @param signatureHeader valor del header X-Hub-Signature-256 (ej.
     *                        {@code sha256=<hex digest>}).
     * @param rawBody         cuerpo crudo del request.
     * @return {@code Mono<Boolean>} true si la firma es valida o si no hay
     *         app-secret configurado (modo mock/local, donde la verificacion
     *         se omite por comodidad en desarrollo).
     */
    public Mono<Boolean> verify(String signatureHeader, String rawBody) {
        String appSecret = properties.api().appSecret();
        if (appSecret == null || appSecret.isBlank()) {
            log.warn("app-secret no configurado; se omite verificacion de firma (modo mock/local)");
            return Mono.just(true);
        }

        if (signatureHeader == null || rawBody == null) {
            return Mono.just(false);
        }

        String signatureClean = signatureHeader.startsWith(SHA256_PREFIX)
                ? signatureHeader.substring(SHA256_PREFIX.length())
                : signatureHeader;

        try {
            byte[] bodyBytes = rawBody.getBytes(StandardCharsets.UTF_8);
            byte[] expectedDigest = hmacSha256(appSecret, bodyBytes);
            byte[] providedDigest = hexToBytes(signatureClean);

            boolean valid = java.security.MessageDigest.isEqual(expectedDigest, providedDigest);
            if (!valid) {
                log.warn("Firma de webhook invalida (posible ataque o configuracion erronea)");
            }
            return Mono.just(valid);
        } catch (Exception e) {
            log.warn("Error verificando firma del webhook", e);
            return Mono.just(false);
        }
    }

    /** Calcula HMAC-SHA256 del payload usando el appSecret. */
    private static byte[] hmacSha256(String appSecret, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec key = new SecretKeySpec(
                    appSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(key);
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo inicializar HMAC-SHA256", e);
        }
    }

    /** Decodifica hex a bytes. */
    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex inválido: " + hex);
        }
        return HexFormat.of().parseHex(hex);
    }
}
