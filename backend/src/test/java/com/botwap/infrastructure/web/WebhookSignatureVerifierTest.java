package com.botwap.infrastructure.web;

import com.botwap.config.WhatsAppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureVerifierTest {

    private static final String APP_SECRET = "test-app-secret";
    private static final String PAYLOAD = "{\"object\":\"whatsapp_business_account\",\"entry\":[]}";

    private WebhookSignatureVerifier verifier;

    @BeforeEach
    void setUp() {
        WhatsAppProperties props = new WhatsAppProperties(
                new WhatsAppProperties.Client("mock"),
                new WhatsAppProperties.Webhook("test-verify-token"),
                new WhatsAppProperties.Api(APP_SECRET, "token", "123", "waba", "v21.0", "https://graph.facebook.com")
        );
        verifier = new WebhookSignatureVerifier(props);
    }

    @Test
    void validSignatureReturnsTrue() throws Exception {
        String signature = "sha256=" + hexHmac(APP_SECRET, PAYLOAD);

        StepVerifier.create(verifier.verify(signature, PAYLOAD))
                .assertNext(result -> assertThat(result).isTrue())
                .verifyComplete();
    }

    @Test
    void signatureWithoutPrefixIsAccepted() throws Exception {
        String signature = hexHmac(APP_SECRET, PAYLOAD);

        StepVerifier.create(verifier.verify(signature, PAYLOAD))
                .assertNext(result -> assertThat(result).isTrue())
                .verifyComplete();
    }

    @Test
    void invalidSignatureReturnsFalse() {
        StepVerifier.create(verifier.verify("sha256=deadbeef", PAYLOAD))
                .assertNext(result -> assertThat(result).isFalse())
                .verifyComplete();
    }

    @Test
    void nullSignatureReturnsFalse() {
        StepVerifier.create(verifier.verify(null, PAYLOAD))
                .assertNext(result -> assertThat(result).isFalse())
                .verifyComplete();
    }

    @Test
    void nullPayloadReturnsFalse() {
        StepVerifier.create(verifier.verify("sha256=abc", null))
                .assertNext(result -> assertThat(result).isFalse())
                .verifyComplete();
    }

    @Test
    void emptyAppSecretSkipsVerification() {
        WhatsAppProperties props = new WhatsAppProperties(
                new WhatsAppProperties.Client("mock"),
                new WhatsAppProperties.Webhook("token"),
                new WhatsAppProperties.Api("", "", "", "", "", "")
        );
        WebhookSignatureVerifier noSecretVerifier = new WebhookSignatureVerifier(props);

        StepVerifier.create(noSecretVerifier.verify(null, PAYLOAD))
                .assertNext(result -> assertThat(result).isTrue())
                .verifyComplete();
    }

    private static String hexHmac(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
