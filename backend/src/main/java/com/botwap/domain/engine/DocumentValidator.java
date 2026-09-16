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
     * Valida el documento contra el tipo indicado.
     *
     * @param type     tipo de documento elegido por el usuario.
     * @param document numero de documento en texto libre.
     * @return resultado con el mensaje a mostrar cuando no es válido.
     */
    public static ValidationResult validate(DocumentType type, String document) {
        if (type == null) {
            return ValidationResult.invalid("Tipo de identificacion invalido.");
        }
        if (document == null || document.isBlank()) {
            return ValidationResult.invalid("El numero de documento no puede estar vacio.");
        }
        String trimmed = document.trim();
        return switch (type) {
            case CC -> validateNumeric(trimmed, 5, 10, "Cedula de ciudadania");
            case NIT -> validateNumeric(trimmed, 8, 12, "NIT");
            case PASSPORT -> validateAlphanumeric(trimmed, 5, 10, "Pasaporte");
            case NEW_CLIENT -> ValidationResult.ok();
        };
    }

    /** Tipo de documento a partir del {@code optionKey} del menu de identificacion. */
    public static DocumentType fromOptionKey(String optionKey) {
        if (optionKey == null) {
            return null;
        }
        for (DocumentType type : DocumentType.values()) {
            if (type.name().equals(optionKey)) {
                return type;
            }
        }
        return null;
    }

    /** Sigla/etiqueta corta del tipo de documento (para resumenes). */
    public static String shortLabel(String optionKey) {
        DocumentType type = fromOptionKey(optionKey);
        if (type == null) {
            return optionKey == null ? "-" : optionKey;
        }
        return switch (type) {
            case CC -> "CC";
            case NIT -> "NIT";
            case PASSPORT -> "Pasaporte";
            case NEW_CLIENT -> "Cliente nuevo";
        };
    }

    private static ValidationResult validateNumeric(String value, int minLen, int maxLen, String label) {
        if (!value.matches("\\d+")) {
            return ValidationResult.invalid(label + ": ingresa solo numeros.");
        }
        int len = value.length();
        if (len < minLen || len > maxLen) {
            return ValidationResult.invalid(
                    label + ": longitud invalida (debe tener entre " + minLen + " y " + maxLen + " digitos).");
        }
        return ValidationResult.ok();
    }

    private static ValidationResult validateAlphanumeric(String value, int minLen, int maxLen, String label) {
        if (!value.matches("[A-Za-z0-9]+")) {
            return ValidationResult.invalid(label + ": ingresa solo letras y/o numeros.");
        }
        int len = value.length();
        if (len < minLen || len > maxLen) {
            return ValidationResult.invalid(
                    label + ": longitud invalida (debe tener entre " + minLen + " y " + maxLen + " caracteres).");
        }
        return ValidationResult.ok();
    }
}