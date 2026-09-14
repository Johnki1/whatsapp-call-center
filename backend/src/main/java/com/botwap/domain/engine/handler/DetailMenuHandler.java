package com.botwap.domain.engine.handler;

import com.botwap.domain.engine.InputNormalizer;
import com.botwap.domain.engine.StateHandler;
import com.botwap.domain.model.ConversationSelection;
import com.botwap.domain.model.ConversationState;
import com.botwap.domain.menu.MenuCatalog;
import com.botwap.domain.menu.MenuOption;

import java.util.List;

/**
 * Handler del Nivel 3: DETAIL_MENU.
 *
 * <p>Sub-menu de detalle para las categorias secundarias (Recargas, Quejas,
 * Informacion personal, Soporte). Cualquier opcion valida avanza a
 * IDENTIFICATION_MENU (N4) y registra una SELECCION DE NIVEL 3. La categoria
 * de origen se deriva de la seleccion de nivel 2 ya persistida (PostgreSQL
 * como fuente de verdad), sin estado adicional en memoria.</p>
 *
 * <p>La opcion 3 aqui NO es \"Quejas\": interpreta exclusivamente el contexto
 * de DETAIL_MENU (los numeros dependen del estado).</p>
 */
public final class DetailMenuHandler implements StateHandler {

    @Override
    public Outcome handle(Context ctx) {
        String normalized = InputNormalizer.normalize(ctx.input());

        if (InputNormalizer.isGlobalCommand(normalized)) {
            return handleGlobal(normalized, originState(ctx));
        }

        List<MenuOption> options = optionsFor(ctx);
        if (options.isEmpty()) {
            return Outcome.textOnly(
                    "No se encontro el detalle para tu seleccion. Escribe 'menu' para volver al inicio.",
                    ConversationState.MAIN_MENU);
        }

        try {
            int option = Integer.parseInt(normalized);
            if (option < 1 || option > options.size()) {
                return Outcome.textOnly(
                        "Opcion no valida. Selecciona entre 1 y " + options.size() + ".\n\n"
                                + formatOptions(options),
                        ConversationState.DETAIL_MENU);
            }
            MenuOption selected = options.get(option - 1);
            return new Outcome(
                    "Seleccionaste: " + selected.displayLabel() + ".\n\n"
                            + "Para continuar necesitamos identificarte.\n\n"
                            + "Selecciona tu tipo de identificacion:\n\n"
                            + "1. Cedula de ciudadania\n2. Pasaporte\n3. NIT\n4. Soy cliente nuevo",
                    ConversationState.IDENTIFICATION_MENU,
                    ConversationSelection.unpersisted(3, originState(ctx), selected.optionKey(), selected.displayLabel(), "{}"));
        } catch (NumberFormatException e) {
            return Outcome.textOnly(
                    "Entrada no reconocida. Selecciona entre 1 y " + options.size() + ".\n\n"
                            + formatOptions(options),
                    ConversationState.DETAIL_MENU);
        }
    }

    /** Categoria de origen (estado N2) desde la seleccion de nivel 2 persistida. */
    private static String originState(Context ctx) {
        return ctx.selections().stream()
                .filter(s -> s.level() == 2)
                .map(ConversationSelection::stateKey)
                .findFirst()
                .orElse("MAIN_MENU");
    }

    private static List<MenuOption> optionsFor(Context ctx) {
        return MenuCatalog.detailOptionsFor(originState(ctx));
    }

    private Outcome handleGlobal(String cmd, String origin) {
        ConversationState back = ConversationState.valueOf(origin);
        return switch (cmd) {
            case "menu" -> Outcome.textOnly(
                    "Hola! Bienvenido.\n\nSelecciona una opcion:\n\n"
                            + "1. Compra de paquetes\n2. Recargas\n3. Quejas o reclamos\n4. Informacion personal\n5. Soporte tecnico",
                    ConversationState.MAIN_MENU);
            case "cancelar" -> Outcome.textOnly(
                    "Tu conversacion ha sido cancelada. Escribe 'hola' para comenzar de nuevo.",
                    ConversationState.CANCELLED);
            case "volver" -> Outcome.textOnly(
                    "Regresando.\n\n" + formatOptions(MenuCatalog.optionsFor(origin)),
                    back);
            default -> Outcome.textOnly(
                    "Selecciona una opcion valida.",
                    ConversationState.DETAIL_MENU);
        };
    }

    private static String formatOptions(List<MenuOption> options) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (MenuOption opt : options) {
            sb.append(i).append(". ").append(opt.displayLabel()).append("\n");
            i++;
        }
        return sb.toString().trim();
    }
}