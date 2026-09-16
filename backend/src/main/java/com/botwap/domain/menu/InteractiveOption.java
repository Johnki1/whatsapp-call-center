package com.botwap.domain.menu;

import java.util.List;

/**
 * Opción interactiva nativa de WhatsApp: identificador estable y título visible.
 *
 * <p>El {@code id} se envía a Meta como {@code reply.id} (botón) o
 * {@code row.id} (lista) y regresa en el webhook como
 * {@code interactive.button_reply.id} / {@code list_reply.id}; el motor lo
 * interpreta como la opción elegida (por {@code optionKey}).</p>
 *
 * <p>Meta limita los títulos (20 caracteres en botones, 24 en filas de lista);
 * el límite se aplica al construir el payload, no aquí.</p>
 *
 * @param id    identificador estable (el {@code optionKey} del catálogo)
 * @param title título visible sin emoji (el emoji lo aporta el catálogo)
 */
public record InteractiveOption(String id, String title) {

    public InteractiveOption {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("InteractiveOption.id is required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("InteractiveOption.title is required");
        }
    }

    /** Convierte una opción del catálogo en opción interactiva. */
    public static InteractiveOption of(MenuOption option) {
        return new InteractiveOption(option.optionKey(), option.label());
    }

    /** Convierte una lista completa de opciones del catálogo. */
    public static List<InteractiveOption> listOf(List<MenuOption> options) {
        return options.stream().map(InteractiveOption::of).toList();
    }
}
