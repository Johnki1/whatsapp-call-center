package com.botwap.domain.engine;

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
}