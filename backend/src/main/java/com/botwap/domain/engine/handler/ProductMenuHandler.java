package com.botwap.domain.engine.handler;

import com.botwap.domain.engine.InputNormalizer;
import com.botwap.domain.engine.StateHandler;
import com.botwap.domain.model.ConversationSelection;
import com.botwap.domain.model.ConversationState;
import com.botwap.domain.menu.MenuCatalog;
import com.botwap.domain.menu.MenuOption;

import java.util.List;

/**
 * Handler del Nivel 3: PRODUCT_MENU.
 *
 * <p>Selecciona un paquete de datos. La opcion 3 aqui (20 GB) NO significa
 * "Quejas" — su interpretacion es exclusiva del estado PRODUCT_MENU.</p>
 */
public final class ProductMenuHandler implements StateHandler {

    @Override
    public Outcome handle(Context ctx) {
        String normalized = InputNormalizer.normalize(ctx.input());

        if (InputNormalizer.isGlobalCommand(normalized)) {
            return handleGlobal(normalized);
        }

        try {
            int option = Integer.parseInt(normalized);
            List<MenuOption> options = MenuCatalog.productMenu();
            if (option < 1 || option > options.size()) {
                return Outcome.textOnly(
                        "Opcion no valida. Selecciona entre 1 y " + options.size() + ".\n\n"
                                + "Selecciona tu paquete:\n\n" + formatOptions(options),
                        ConversationState.PRODUCT_MENU);
            }
            MenuOption selected = options.get(option - 1);
            return new Outcome(
                    "Has seleccionado: " + selected.displayLabel() + ".\n\n"
                            + "Para continuar necesitamos identificarte.",
                    ConversationState.IDENTIFICATION_MENU,
                    ConversationSelection.unpersisted(3, "PRODUCT_MENU", selected.optionKey(), selected.displayLabel(), "{}"));
        } catch (NumberFormatException e) {
            List<MenuOption> options = MenuCatalog.productMenu();
            return Outcome.textOnly(
                    "Entrada no reconocida. Selecciona entre 1 y " + options.size() + ".\n\n"
                            + "Selecciona tu paquete:\n\n" + formatOptions(options),
                    ConversationState.PRODUCT_MENU);
        }
    }

    private Outcome handleGlobal(String cmd) {
        return switch (cmd) {
            case "menu" -> Outcome.textOnly(
                    "Hola! Bienvenido.\n\nSelecciona una opcion:\n\n" + formatOptions(MenuCatalog.mainMenu()),
                    ConversationState.MAIN_MENU);
            case "cancelar" -> Outcome.textOnly(
                    "Tu conversacion ha sido cancelada. Escribe 'hola' para comenzar de nuevo.",
                    ConversationState.CANCELLED);
            case "volver" -> Outcome.textOnly(
                    "Selecciona una opcion:\n\n" + formatOptions(MenuCatalog.purchaseMenu()),
                    ConversationState.PURCHASE_MENU);
            default -> Outcome.textOnly("Selecciona una opcion valida.", ConversationState.PRODUCT_MENU);
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