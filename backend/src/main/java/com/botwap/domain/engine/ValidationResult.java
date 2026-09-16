package com.botwap.domain.engine;

/**
 * Resultado de una validación de entrada del usuario (nombre, documento, etc.).
 *
 * <p>El {@code message} es el texto que se muestra al usuario cuando la entrada
 * no es válida; incluye el tono y el formato nativo de WhatsApp del bot.</p>
 *
 * @param valid   {@code true} si la entrada pasó la validación.
 * @param message explicación mostrada al usuario cuando {@code valid} es {@code false}.
 */
public record ValidationResult(boolean valid, String message) {

    /** Entrada válida (sin mensaje). */
    public static ValidationResult ok() {
        return new ValidationResult(true, "");
    }

    /** Entrada inválida con el mensaje a mostrar al usuario. */
    public static ValidationResult invalid(String message) {
        return new ValidationResult(false, message);
    }
}
