package com.botwap.domain.engine;

import java.util.regex.Pattern;

/**
 * Validador del nombre completo del usuario (primer paso del Nivel 4 —
 * Identificación).
 *
 * <p>Reglas:
 * <ul>
 *   <li>Solo letras (incluye acentos y alfabetos no latinos), espacios,
 *       apóstrofes, puntos y guiones.</li>
 *   <li>Entre 3 y {@value #MAX_LENGTH} caracteres.</li>
 *   <li>No admite números ni símbolos (evita inyección de formato y JSON
 *       inválido en {@code conversation_selection.metadata}).</li>
 * </ul>
 */
public final class NameValidator {

    /** Longitud máxima admitida (alineada con {@code display_label VARCHAR(120)}). */
    public static final int MAX_LENGTH = 80;

    private static final int MIN_LENGTH = 3;

    /** Letras Unicode (\p{L}), marcas diacríticas (\p{M}) y separadores de nombre. */
    private static final Pattern VALID_NAME =
            Pattern.compile("^[\\p{L}\\p{M}][\\p{L}\\p{M}' .-]*$");

    private NameValidator() {
    }

    /** Valida el nombre completo recibido como texto libre. */
    public static ValidationResult validate(String input) {
        String name = normalize(input);
        if (name.isEmpty()) {
            return ValidationResult.invalid("👤 Necesito tu *nombre completo* para continuar.");
        }
        if (name.length() < MIN_LENGTH) {
            return ValidationResult.invalid(
                    "🤔 El nombre es muy corto. Escríbelo completo, por favor.");
        }
        if (name.length() > MAX_LENGTH) {
            return ValidationResult.invalid(
                    "🤔 El nombre es muy largo (máximo " + MAX_LENGTH + " caracteres).");
        }
        if (!VALID_NAME.matcher(name).matches()) {
            return ValidationResult.invalid(
                    "🤔 Por favor ingresa solo *letras y espacios* (sin números ni símbolos).");
        }
        return ValidationResult.ok();
    }

    /**
     * Normaliza el nombre para persistirlo: colapsa espacios y recorta extremos.
     *
     * <p>Devuelve cadena vacía si la entrada es {@code null} o solo espacios.</p>
     */
    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        return input.trim().replaceAll("\\s+", " ");
    }
}
