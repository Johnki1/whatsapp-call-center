package com.botwap.domain.engine.handler;

import com.botwap.domain.engine.DocumentValidator;
import com.botwap.domain.engine.InputNormalizer;
import com.botwap.domain.engine.StateHandler;
import com.botwap.domain.model.ConversationSelection;
import com.botwap.domain.model.ConversationState;
import com.botwap.domain.menu.MenuOption;
import com.botwap.domain.menu.MenuCatalog;

import java.util.List;

/**
 * Handler del Nivel 4: IDENTIFICATION_MENU.
 *
 * <p>Permite al usuario escoger el tipo de documento. El ingreso del NUMERO
 * de documento se maneja en DOCUMENT_INPUT, pero tambien pertenece al NIVEL 4
 * (no se crea un nivel 6).</p>
 */
public final class IdentificationMenuHandler implements StateHandler {

    @Override
    public Outcome handle(Context ctx) {
        String normalized = InputNormalizer.normalize(ctx.input());

        if (InputNormalizer.isGlobalCommand(normalized)) {
            return handleGlobal(normalized);
        }

        try {
            int option = Integer.parseInt(normalized);
            List<MenuOption> options = MenuCatalog.identificationMenu();
            if (option < 1 || option > options.size()) {
                return Outcome.textOnly(
                        "Opcion no valida. Selecciona entre 1 y " + options.size() + ".\n\n"
                                + "Selecciona tu tipo de identificacion:\n\n" + formatOptions(options),
                        ConversationState.IDENTIFICATION_MENU);
            }
            MenuOption selected = options.get(option - 1);
            String metadata = "{\"documentType\":\"" + selected.optionKey() + "\"}";
            return new Outcome(
                    "Seleccionaste: " + selected.displayLabel() + ".\n\n"
                            + "Ingresa tu numero de documento.",
                    ConversationState.DOCUMENT_INPUT,
                    ConversationSelection.unpersisted(4, "IDENTIFICATION_MENU", selected.optionKey(), selected.displayLabel(), metadata));
        } catch (NumberFormatException e) {
            List<MenuOption> options = MenuCatalog.identificationMenu();
            return Outcome.textOnly(
                    "Entrada no reconocida. Selecciona entre 1 y " + options.size() + ".\n\n"
                            + "Selecciona tu tipo de identificacion:\n\n" + formatOptions(options),
                    ConversationState.IDENTIFICATION_MENU);
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
                    "Selecciona tu paquete:\n\n" + formatOptions(MenuCatalog.productMenu()),
                    ConversationState.PRODUCT_MENU);
            default -> Outcome.textOnly("Selecciona una opcion valida.", ConversationState.IDENTIFICATION_MENU);
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