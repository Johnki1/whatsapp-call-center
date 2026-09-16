package com.botwap.infrastructure.ai;

import com.botwap.config.GeminiProperties;
import com.botwap.domain.port.AiAssistant;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Adaptador real del asistente de IA: Google Gemini (Generative Language API).
 *
 * <p>Invoca {@code POST {baseUrl}/v1beta/models/{model}:generateContent} con la
 * API key como query param ({@code GEMINI_API_KEY}, proveniente del Key Vault).
 * El modelo por defecto es {@code gemini-3.5-flash-lite}, la variante más
 * económica: el coste operativo se mantiene en ~$0 al invocarla solo para texto
 * libre y limitar {@code maxOutputTokens}.</p>
 *
 * <p>Contrato con el modelo: responde JSON estricto
 * {@code {"reply": "…", "intent": "…"}}. Cualquier fallo (HTTP, formato,
 * respuesta vacía) se degrada a {@link AiReply#empty()} para que el orquestador
 * use la máquina de estados pura: la IA es una mejora, nunca un requisito.</p>
 *
 * <p>La API key nunca se registra en logs.</p>
 */
@Component
public class GeminiAiClient implements AiAssistant {

    private static final Logger log = LoggerFactory.getLogger(GeminiAiClient.class);

    static final String GENERATE_CONTENT_PATH = "/v1beta/models/{model}:generateContent";
    private static final String RESPONSE_MIME_JSON = "application/json";
    private static final int MAX_OUTPUT_TOKENS = 256;

    private final GeminiProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient;

    public GeminiAiClient(GeminiProperties properties, WebClient.Builder builder) {
        this.properties = properties;
        long timeoutMs = properties.timeoutMs() > 0 ? properties.timeoutMs() : 8_000L;

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                .responseTimeout(Duration.ofMillis(timeoutMs));

        this.webClient = builder
                .baseUrl(properties.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

        // Solo configuración NO sensible: la API key nunca se loguea.
        log.info("GeminiAiClient habilitado={} model={} timeoutMs={}",
                properties.enabled(), properties.model(), timeoutMs);
    }

    @Override
    public boolean isEnabled() {
        return properties.isConfigured();
    }

    @Override
    public Mono<AiReply> assist(AiRequest request) {
        if (!isEnabled()) {
            return Mono.error(new IllegalStateException(
                    "Gemini AI no está configurado (GEMINI_API_KEY ausente o deshabilitado)"));
        }
        if (request.userText() == null || request.userText().isBlank()) {
            return Mono.error(new IllegalArgumentException("userText is required"));
        }

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path(GENERATE_CONTENT_PATH)
                        .queryParam("key", properties.apiKey())
                        .build(properties.model()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody(request))
                .retrieve()
                .bodyToMono(String.class)
                .map(GeminiAiClient::parse)
                .doOnError(error -> log.warn("GeminiAiClient: fallo al interpretar texto libre ({})",
                        error.getClass().getSimpleName()));
    }

    /** Cuerpo de la petición generateContent (prompt de sistema + turno del usuario). */
    Map<String, Object> requestBody(AiRequest request) {
        return Map.of(
                "systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", systemPrompt(request)))),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", request.userText())))),
                "generationConfig", Map.of(
                        "temperature", 0.4,
                        "maxOutputTokens", MAX_OUTPUT_TOKENS,
                        "responseMimeType", RESPONSE_MIME_JSON));
    }

    /**
     * Prompt de sistema: rol, contexto de la conversación (usuario, estado,
     * opciones del menú), reglas de tono y formato de salida JSON.
     */
    static String systemPrompt(AiRequest request) {
        String user = request.userName() == null || request.userName().isBlank()
                ? "no identificado"
                : request.userName();
        String options = request.menuOptions() == null || request.menuOptions().isEmpty()
                ? "ninguna"
                : String.join("; ", request.menuOptions());

        return """
                Eres el asistente virtual de BotWap, operador de telefonía móvil en Colombia. \
                Atiendes por WhatsApp con tono amable, cercano y profesional; usas emojis con \
                moderación y formato de WhatsApp (*negrita*).

                Datos de la conversación:
                - Usuario: %s
                - Estado actual: %s
                - Opciones del menú disponibles: %s

                Tarea: si el usuario pide algo que corresponde a una opción del menú, clasifica \
                su intención con el valor correspondiente; si quiere hablar con una persona, usa \
                la intención AGENT; si es un saludo o algo que no encaja, usa GREETING u OTHER y \
                guíalo con amabilidad hacia las opciones disponibles. No prometas precios, \
                descuentos ni plazos; nunca pidas claves, códigos ni datos de tarjetas.

                Responde EXCLUSIVAMENTE con JSON, sin texto adicional:
                {"reply": "<máximo 2 frases, en español, amable y profesional>", \
                "intent": "GREETING|PURCHASE|RECHARGE|COMPLAINT|PERSONAL_INFO|SUPPORT|AGENT|OTHER"}"""
                .formatted(user, request.conversationState(), options);
    }

    /**
     * Parsea la respuesta de Gemini de forma defensiva.
     *
     * <p>Extrae {@code candidates[0].content.parts[*].text} y delega en
     * {@link #parseReply(String)}. Cualquier anomalía devuelve
     * {@link AiReply#empty()} (fail-open).</p>
     */
    static AiReply parse(String responseBody) {
        try {
            JsonNode root = new ObjectMapper().readTree(responseBody);
            StringBuilder text = new StringBuilder();
            for (JsonNode part : root.path("candidates").path(0).path("content").path("parts")) {
                text.append(part.path("text").asText(""));
            }
            return parseReply(text.toString());
        } catch (Exception e) {
            return AiReply.empty();
        }
    }

    /** Parsea el JSON de respuesta del modelo (tolera cercos de código). */
    static AiReply parseReply(String modelOutput) {
        if (modelOutput == null) {
            return AiReply.empty();
        }
        String cleaned = modelOutput.trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return AiReply.empty();
        }
        try {
            JsonNode node = new ObjectMapper().readTree(cleaned.substring(start, end + 1));
            String reply = node.path("reply").asText("");
            String intent = node.path("intent").asText("OTHER");
            return new AiReply(reply.isBlank() ? "" : reply.trim(),
                    intent.isBlank() ? "OTHER" : intent.trim());
        } catch (Exception e) {
            return AiReply.empty();
        }
    }
}
