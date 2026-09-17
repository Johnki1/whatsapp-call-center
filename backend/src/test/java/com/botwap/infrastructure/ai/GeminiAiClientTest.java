package com.botwap.infrastructure.ai;

import com.botwap.domain.port.AiAssistant.AiReply;
import com.botwap.domain.port.AiAssistant.AiRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiAiClientTest {

    @Test
    void acceptsLegacyReplyWithoutConfidence() {
        assertThat(GeminiAiClient.parseReply("{\"reply\":\" Hola \",\"intent\":\"SUPPORT\"}"))
                .isEqualTo(new AiReply("Hola", "SUPPORT"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.8", "0.95", "1"})
    void acceptsConfidentIntent(String confidence) {
        assertThat(GeminiAiClient.parseReply(response(confidence)).intent()).isEqualTo("SUPPORT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.79", "0", "-1", "1.1", "null", "\"high\"", "true", "{}"})
    void uncertainOrInvalidConfidenceKeepsFriendlyReplyWithoutRouting(String confidence) {
        assertThat(GeminiAiClient.parseReply(response(confidence)))
                .isEqualTo(new AiReply("Te ayudo", "OTHER"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not json", "{}", "{\"reply\":true,\"intent\":\"SUPPORT\"}",
            "{\"reply\":\"Hola\",\"intent\":null}", "{\"reply\":\"Hola\"}"})
    void malformedReplyFallsBack(String output) {
        assertThat(GeminiAiClient.parseReply(output)).isEqualTo(AiReply.empty());
    }

    @Test
    void extractsReplyFromGeminiEnvelopeAndCodeFence() {
        String envelope = """
                {"candidates":[{"content":{"parts":[{"text":"```json\\n"},
                {"text":"{\\"reply\\":\\"Te ayudo\\",\\"intent\\":\\"SUPPORT\\",\\"confidence\\":0.9}"},
                {"text":"\\n```"}]}}]}
                """;
        assertThat(GeminiAiClient.parse(envelope)).isEqualTo(new AiReply("Te ayudo", "SUPPORT"));
        assertThat(GeminiAiClient.parse("{\"candidates\":[]}")).isEqualTo(AiReply.empty());
    }

    @Test
    void promptDocumentsActualStatesAndAmbiguity() {
        String prompt = GeminiAiClient.systemPrompt(new AiRequest(null, "COMPLAINT_MENU",
                List.of("SPEED_QUALITY: Velocidad y calidad"), "Mi internet está lento"));
        assertThat(prompt).contains("PURCHASE_MENU", "RECHARGE_MENU", "SUPPORT_MENU",
                "SPEED_QUALITY", "OTHER", "confidence", "0.8", "NO es una compra");
    }

    private static String response(String confidence) {
        return "{\"reply\":\"Te ayudo\",\"intent\":\"SUPPORT\",\"confidence\":" + confidence + "}";
    }
}
