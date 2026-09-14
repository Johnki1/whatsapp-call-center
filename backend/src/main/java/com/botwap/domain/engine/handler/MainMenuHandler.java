package com.botwap.domain.engine.handler;

import com.botwap.domain.engine.InputNormalizer;
import com.botwap.domain.engine.StateHandler;
import com.botwap.domain.model.ConversationSelection;
import com.botwap.domain.model.ConversationState;
import com.botwap.domain.menu.MenuOption;
import com.botwap.domain.menu.MenuCatalog;

import java.util.List;

/**
 * Handler del Nivel 1: MENU_PRINCIPAL.
 *
 * <p>Acepta:
 * - Saludos (hola) → repite el menu.
 * - Numeros 1-5 → categoria correspondiente (N2).
 * - Entradas invalidas → re-prompt sin cambiar de estado.</p>
 */
public final class MainMenuHandler implements StateHandler {

    @Override
    public Outcome handle(Context ctx) {
        String normalized = InputNormalizer.normalize(ctx.input());

        if (InputNormalizer.isGlobalCommand(normalized)) {
            return handleGlobal(normalized);
        }

        if (InputNormalizer.isHello(normalized)) {
            return Outcome.textOnly(renderMenu(), ConversationState.MAIN_MENU);
        }

        try {
            int option = Integer.parseInt(normalized);
            return handleNumeric(option);
        } catch (NumberFormatException e) {
            return Outcome.textOnly(
                    "Entrada no reconocida. Selecciona 1, 2, 3, 4 o 5.\n\n" + renderMenu(),
                    ConversationState.MAIN_MENU);
        }
    }

    private Outcome handleGlobal(String cmd) {
        return switch (cmd) {
            case "menu" -> Outcome.textOnly(renderMenu(), ConversationState.MAIN_MENU);
            case "cancelar" ->
                    Outcome.textOnly("Tu conversacion ha sido cancelada. Escribe 'hola' para comenzar de nuevo.", ConversationState.CANCELLED);
            case "volver" -> Outcome.textOnly(
                    "Estas en el menu principal. Selecciona una opcion.\n\n" + renderMenu(),
                    ConversationState.MAIN_MENU);
            default -> Outcome.textOnly(renderMenu(), ConversationState.MAIN_MENU);
        };
    }

    private Outcome handleNumeric(int option) {
        List<MenuOption> opts = MenuCatalog.mainMenu();
        if (option < 1 || option > opts.size()) {
            return Outcome.textOnly(
                    "Opcion no valida. Selecciona 1, 2, 3, 4 o 5.\n\n" + renderMenu(),
                    ConversationState.MAIN_MENU);
        }
        MenuOption selected = opts.get(option - 1);
        return transitionTo(selected);
    }

    private Outcome transitionTo(MenuOption option) {
        return switch (option.optionKey()) {
            case "PURCHASE" -> new Outcome(
                    "¿Que deseas comprar?\n\n" + formatOptions(MenuCatalog.purchaseMenu()),
                    ConversationState.PURCHASE_MENU,
                    selectionFor("MAIN_MENU", "PURCHASE", option.displayLabel()));
            case "RECHARGE" -> new Outcome(
                    "¿Que deseas recargar?\n\n" + formatOptions(MenuCatalog.rechargeMenu()),
                    ConversationState.RECHARGE_MENU,
                    selectionFor("MAIN_MENU", "RECHARGE", option.displayLabel()));
            case "COMPLAINT" -> new Outcome(
                    "¿Sobre que deseas hacer un reclamo?\n\n" + formatOptions(MenuCatalog.complaintMenu()),
                    ConversationState.COMPLAINT_MENU,
                    selectionFor("MAIN_MENU", "COMPLAINT", option.displayLabel()));
            case "PERSONAL_INFO" -> new Outcome(
                    "¿Que informacion deseas?\n\n" + formatOptions(MenuCatalog.personalInfoMenu()),
                    ConversationState.PERSONAL_INFO_MENU,
                    selectionFor("MAIN_MENU", "PERSONAL_INFO", option.displayLabel()));
            case "SUPPORT" -> new Outcome(
                    "¿En que soporte necesitas ayuda?\n\n" + formatOptions(MenuCatalog.supportMenu()),
                    ConversationState.SUPPORT_MENU,
                    selectionFor("MAIN_MENU", "SUPPORT", option.displayLabel()));
            default -> Outcome.textOnly(
                    "Selecciona una opcion valida.\n\n" + renderMenu(),
                    ConversationState.MAIN_MENU);
        };
    }

    private ConversationSelection selectionFor(String stateKey, String optionKey, String label) {
        return ConversationSelection.unpersisted(1, stateKey, optionKey, label, "{}");
    }

    private String renderMenu() {
        return "Hola! Bienvenido.\n\nSelecciona una opcion:\n\n" + formatOptions(MenuCatalog.mainMenu());
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