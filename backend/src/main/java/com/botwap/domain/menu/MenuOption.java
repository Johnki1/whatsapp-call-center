package com.botwap.domain.menu;

/**
 * Opción de un menú: clave semántica para persistencia, emoji y etiqueta visible.
 *
 * <p>La separación entre {@code emoji} y {@code label} evita duplicar emojis en
 * los textos que interpolan la etiqueta (resúmenes, acuses, despedidas): los
 * menús usan {@link #displayLabel()} y la prosa usa {@link #label()}.</p>
 *
 * @param optionKey clave estable (p. ej. {@code INTERNET_MOBILE}, se guarda
 *                  en {@code conversation_selection.option_key})
 * @param emoji     emoji representativo mostrado en los menús (cadena vacía si no aplica)
 * @param label     etiqueta de texto sin emoji (p. ej. «Internet móvil»); es la
 *                  que se persiste en {@code display_label} y se usa en la prosa
 */
public record MenuOption(String optionKey, String emoji, String label) {

    /** Opción sin emoji. */
    public MenuOption(String optionKey, String label) {
        this(optionKey, "", label);
    }

    /** Texto mostrado dentro de un menú numerado (emoji + etiqueta). */
    public String displayLabel() {
        return emoji == null || emoji.isBlank() ? label : emoji + " " + label;
    }
}
