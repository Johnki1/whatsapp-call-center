package com.botwap.domain.menu;

/**
 * Opción de un menú: clave semántica para persistencia y etiqueta visible.
 *
 * @param optionKey   clave estable (p. ej. {@code INTERNET_MOBILE}, se guarda
 *                    en {@code conversation_selection.option_key})
 * @param displayLabel texto mostrado al usuario (p. ej. «Internet móvil»)
 */
public record MenuOption(String optionKey, String displayLabel) {
}