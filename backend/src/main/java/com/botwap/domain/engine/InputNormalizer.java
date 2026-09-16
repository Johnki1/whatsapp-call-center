package com.botwap.domain.engine;

import java.util.OptionalInt;
import java.util.List;
import java.util.Optional;

import com.botwap.domain.menu.MenuOption;

/**
 * Normaliza entradas de texto para el motor conversacional.
 *
 * <p>Reglas:
 * - trim y lowercase;
 * - eliminar signos de puntuacion superpuestos al texto (ej. "hola!"); si el resultado
 *   despues de quitar puntuacion es valido, se usa;
 * - comandos globales (menu, volver, cancelar) reconocidos aqui.</p>
 */
public final class InputNormalizer {

    private InputNormalizer() {
    }

    /** Devuelve la entrada normalizada (trim + lowercase, sin puntuacion superpuesta). */
    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String trimmed = input.trim();
        String lower = trimmed.toLowerCase();
        // Quitar puntuacion final superpuesta (ej. "hola!" -> "hola")
        String stripped = lower.replaceAll("[!¡¿?.,;]+$", "");
        return stripped.trim();
    }

    /** Indica si la entrada normalizada coincide con un comando global. */
    public static boolean isGlobalCommand(String normalized) {
        return "menu".equals(normalized)
                || "volver".equals(normalized)
                || "cancelar".equals(normalized)
                || "/menu".equals(normalized)
                || "/volver".equals(normalized)
                || "/cancelar".equals(normalized);
    }

    /**
     * Indica si la entrada reinicia la conversación (saludo o comando de menú).
     *
     * <p>Se usa como único disparador aceptado cuando la última conversación del
     * usuario ya está cerrada: cualquier otro mensaje se registra pero NO genera
     * respuesta (evita el bucle de respuestas automáticas tras el estado final).</p>
     */
    public static boolean isRestartCommand(String normalized) {
        return isHello(normalized) || "menu".equals(normalized) || "/menu".equals(normalized);
    }

    /** Indica si la entrada normalizada es "hola" (inicio del bot). */
    public static boolean isHello(String normalized) {
        return "hola".equals(normalized)
                || "buenas".equals(normalized)
                || "buen dia".equals(normalized)
                || "buenos dias".equals(normalized)
                || "buenas tardes".equals(normalized)
                || "buenas noches".equals(normalized)
                || "holi".equals(normalized)
                || "inicio".equals(normalized)
                || "iniciar".equals(normalized)
                || "empezar".equals(normalized)
                || "/start".equals(normalized);
    }

    /**
     * Interpreta la entrada normalizada como número de opción.
     *
     * @return el número, o vacío si la entrada no es un entero válido.
     */
    public static OptionalInt asNumber(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(normalized.trim()));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    /**
     * Resuelve la opción elegida dentro de un menú a partir de la entrada.
     *
     * <p>Acepta las dos vías de selección:
     * <ul>
     *   <li><strong>Toque en la UI nativa</strong>: el id que regresa en
     *       {@code interactive.button_reply.id} / {@code list_reply.id}
     *       (coincide con el {@code optionKey} del catálogo).</li>
     *   <li><strong>Texto con número</strong> (compatibilidad): {@code 1}, {@code 2}…</li>
     * </ul>
     */
    public static Optional<MenuOption> matchOption(List<MenuOption> options, String normalized) {
        if (options == null || options.isEmpty() || normalized == null || normalized.isBlank()) {
            return Optional.empty();
        }
        String candidate = normalized.trim();
        for (MenuOption option : options) {
            if (option.optionKey().equalsIgnoreCase(candidate)) {
                return Optional.of(option);
            }
        }
        OptionalInt number = asNumber(candidate);
        if (number.isPresent()) {
            int index = number.getAsInt();
            if (index >= 1 && index <= options.size()) {
                return Optional.of(options.get(index - 1));
            }
        }
        return Optional.empty();
    }
}