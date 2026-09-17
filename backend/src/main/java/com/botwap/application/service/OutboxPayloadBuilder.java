package com.botwap.application.service;

import com.botwap.domain.engine.BotCopy;
import com.botwap.domain.engine.EngineResult;
import com.botwap.domain.menu.InteractiveOption;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Construye el payload JSON que viaja en {@code outbox_message.payload} y que el
 * {@code WhatsAppClient} traduce a la API de Meta.
 *
 * <p>Dos formas de payload:
 * <ul>
 *   <li><strong>Texto</strong>: {@code {"text": "…"}} — respuestas de prosa.</li>
 *   <li><strong>Interactivo</strong>: {@code {"text": "…", "interactive": {…}}}
 *       — menús nativos de WhatsApp. Con ≤3 opciones se usan
 *       {@code interactive.type=button}; con más, {@code interactive.type=list}.</li>
 * </ul>
 *
 * <p>Los ids de cada botón/fila son el {@code optionKey} del catálogo, de modo
 * que cuando Meta los devuelve en {@code interactive.button_reply.id} /
 * {@code list_reply.id} el motor reconoce la opción elegida sin estado extra.</p>
 *
 * <p>Aplica los límites de la Cloud API (títulos: 20 caracteres en botones y 24
 * en filas de lista; cuerpo: 1024) truncando de forma defensiva.</p>
 */
@Component
public class OutboxPayloadBuilder {

    public static final int MAX_BUTTON_TITLE = 20;
    public static final int MAX_ROW_TITLE = 24;
    public static final int MAX_BODY_LENGTH = 1024;
    public static final int MAX_LIST_SECTION_TITLE = 24;
    public static final int MAX_BUTTONS = 3;

    static final String INTERACTIVE_TYPE_BUTTON = "button";
    static final String INTERACTIVE_TYPE_LIST = "list";
    static final String LIST_SECTION_TITLE = "Opciones";

    private static final Logger log = LoggerFactory.getLogger(OutboxPayloadBuilder.class);

    private final ObjectMapper objectMapper;

    public OutboxPayloadBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Serializa el resultado del motor al payload del Outbox.
     *
     * @param result resultado del motor (texto y, opcionalmente, menú interactivo)
     * @return JSON listo para {@code outbox_message.payload}
     */
    public String build(EngineResult result) {
        String body = result.responseText() == null ? "" : result.responseText();
        if (!result.hasMenu()) {
            return serialize(Map.of("text", body));
        }
        return serialize(interactivePayload(body, result.options()));
    }

    /** Reconstruye únicamente un payload interno persistido; no ejecuta entradas del usuario. */
    public EngineResult restore(String payload, com.botwap.domain.model.ConversationState state) {
        try {
            var root = objectMapper.readTree(payload);
            var options = new java.util.ArrayList<InteractiveOption>();
            var interactive = root.path("interactive");
            if ("button".equals(interactive.path("type").asText())) {
                for (var button : interactive.path("action").path("buttons")) {
                    options.add(new InteractiveOption(button.path("reply").path("id").asText(),
                            button.path("reply").path("title").asText()));
                }
            } else if ("list".equals(interactive.path("type").asText())) {
                for (var section : interactive.path("action").path("sections")) {
                    for (var row : section.path("rows")) {
                        options.add(new InteractiveOption(row.path("id").asText(), row.path("title").asText()));
                    }
                }
            }
            if (!root.hasNonNull("text")) {
                throw new IllegalStateException("El menú suspendido no contiene texto");
            }
            return EngineResult.menu(root.path("text").asText(), state, null, options);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo recuperar el menú suspendido", e);
        }
    }

    /** Payload interactivo como mapa ordenado (serializable con Jackson). */
    static Map<String, Object> interactivePayload(String body, List<InteractiveOption> options) {
        String prose = truncate(body, MAX_BODY_LENGTH);

        Map<String, Object> interactive = new LinkedHashMap<>();
        interactive.put("type", options.size() <= MAX_BUTTONS
                ? INTERACTIVE_TYPE_BUTTON
                : INTERACTIVE_TYPE_LIST);
        interactive.put("body", Map.of("text", prose));
        interactive.put("footer", Map.of("text", BotCopy.FOOTER));

        if (options.size() <= MAX_BUTTONS) {
            List<Map<String, Object>> buttons = options.stream()
                    .map(option -> Map.<String, Object>of(
                            "type", "reply",
                            "reply", Map.of(
                                    "id", option.id(),
                                    "title", truncate(option.title(), MAX_BUTTON_TITLE))))
                    .toList();
            interactive.put("action", Map.of("buttons", buttons));
        } else {
            List<Map<String, Object>> rows = options.stream()
                    .map(option -> Map.<String, Object>of(
                            "id", option.id(),
                            "title", truncate(option.title(), MAX_ROW_TITLE)))
                    .toList();
            interactive.put("action", Map.of(
                    "button", BotCopy.LIST_BUTTON_LABEL,
                    "sections", List.of(Map.of(
                            "title", truncate(LIST_SECTION_TITLE, MAX_LIST_SECTION_TITLE),
                            "rows", rows))));
        }

        // "text" se mantiene para auditoría y para que el texto del cuerpo sea
        // visible sin deserializar el nodo "interactive".
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", prose);
        payload.put("interactive", interactive);
        return payload;
    }

    /** Trunca por unidades de código UTF-16 (como cuenta WhatsApp los títulos). */
    static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // No debe ocurrir (payload controlado); si ocurre, se degrada a texto vacío
            // para no abortar la transacción del webhook.
            log.error("No se pudo serializar el payload del outbox", e);
            return "{\"text\":\"\"}";
        }
    }
}
