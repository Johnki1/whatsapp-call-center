package com.botwap.domain.engine;

/**
 * Validador de tipo y formato de documento de identidad (Nivel 4).
 *
 * <p>Reglas (sin validaciones externas en esta fase):
 * - CC: solo numeros, 5-10 digitos.
 * - PASSPORT: alfanumerico, 5-10 caracteres.
 * - NIT: solo numeros, 8-12 digitos.
 * - NEW_CLIENT: no requiere documento (texto libre aceptado como "cliente nuevo").</p>
 */
public final class DocumentValidator {

    private DocumentValidator() {
    }

    /** Tipo de documento admitido. */
    public enum DocumentType {
        CC, PASSPORT, NIT, NEW_CLIENT
    }

    /**
     * Resultado de la validacion del documento.
     * @param valid true si el documento pasa la validacion.
     * @param message mensaje explicativo (para mostrar al usuario si no es valido).
     */
    public record ValidateResult(boolean valid, String message) {
    }

    public static ValidateResult validate(DocumentType type, String document) {
        if (type == null) {
            return new ValidateResult(false, "Tipo de identificacion invalido.");
        }
        if (document == null || document.isBlank()) {
            return new ValidateResult(false, "El numero de documento no puede estar vacio.");
        }
        String trimmed = document.trim();
        return switch (type) {
            case CC -> validateNumeric(trimmed, 5, 10, "Cedula de ciudadania");
            case NIT -> validateNumeric(trimmed, 8, 12, "NIT");
            case PASSPORT -> validateAlphanumeric(trimmed, 5, 10, "Pasaporte");
            case NEW_CLIENT -> new ValidateResult(true, "");
        };
    }

    private static ValidateResult validateNumeric(String value, int minLen, int maxLen, String label) {
        if (!value.matches("\\d+")) {
            return new ValidateResult(false, label + ": ingresa solo numeros.");
        }
        int len = value.length();
        if (len < minLen || len > maxLen) {
            return new ValidateResult(false,
                    label + ": longitud invalida (debe tener entre " + minLen + " y " + maxLen + " digitos).");
        }
        return new ValidateResult(true, "");
    }

    private static ValidateResult validateAlphanumeric(String value, int minLen, int maxLen, String label) {
        if (!value.matches("[A-Za-z0-9]+")) {
            return new ValidateResult(false, label + ": ingresa solo letras y/o numeros.");
        }
        int len = value.length();
        if (len < minLen || len > maxLen) {
            return new ValidateResult(false,
                    label + ": longitud invalida (debe tener entre " + minLen + " y " + maxLen + " caracteres).");
        }
        return new ValidateResult(true, "");
    }
}