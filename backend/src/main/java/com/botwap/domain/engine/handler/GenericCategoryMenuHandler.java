package com.botwap.domain.engine.handler;

import com.botwap.domain.engine.InputNormalizer;
import com.botwap.domain.engine.StateHandler;
import com.botwap.domain.model.ConversationSelection;
import com.botwap.domain.model.ConversationState;
import com.botwap.domain.menu.MenuCatalog;
import com.botwap.domain.menu.MenuOption;

import java.util.List;

/**
 * Handler generico para los Menus de categoria del Nivel 2 que no son
 * "Compra de paquetes" (Recargas, Quejas, Informacion personal, Soporte).
 *
 * <p>Las opciones de estos menus conducen a un sub-menu de detalle (DETAIL_MENU,
 * Nivel 3) que a su vez lleva a identificacion. La opcion 3 de RECHARGE_MENU
 * NO significa "Quejas": cada estado interpreta su entrada de forma aislada.</p>
 */
public final class GenericCategoryMenuHandler implements StateHandler {

    private final String originStateKey;
    private final String stateKey;      // clave del estado actual (ej. RECHARGE_MENU)
    private final List<MenuOption> options;
    private final String prompt;
    private final List<MenuOption> nextOptions;

    public GenericCategoryMenuHandler(String originStateKey, String stateKey,
                                      List<MenuOption> options, String prompt,
                                      List<MenuOption> nextOptions) {
        this.originStateKey = originStateKey;
        this.stateKey = stateKey;
        this.options = options;
        this.prompt = prompt;
        this.nextOptions = nextOptions;
    }

    @Override
    public Outcome handle(Context ctx) {
        String normalized = InputNormalizer.normalize(ctx.input());

        if (InputNormalizer.isGlobalCommand(normalized)) {
            return handleGlobal(normalized);
        }

        try {
            int option = Integer.parseInt(normalized);
            if (option < 1 || option > options.size()) {
                return invalidOption(normalized);
            }
            MenuOption selected = options.get(option - 1);
            // Cada categoria del nivel 2 navega a su sub-menu de detalle (N3).
            // Solo se registra la SELECCION DE NIVEL 2; la del N3 la registra
            // DetailMenuHandler cuando el usuario elige dentro del sub-menu.
            return new Outcome(
                    detailPrompt(selected) + "\n\n" + formatOptionsStatic(nextOptions),
                    ConversationState.DETAIL_MENU,
                    ConversationSelection.unpersisted(2, stateKey, selected.optionKey(), selected.displayLabel(), "{}"));
        } catch (NumberFormatException e) {
            return invalidOption(normalized);
        }
    }

    private Outcome invalidOption(String input) {
        if (input == null || input.isBlank()) {
            return Outcome.textOnly(prompt + "\n\n" + formatOptions(), ConversationState.valueOf(stateKey));
        }
        return Outcome.textOnly(
                "Opcion no valida. Selecciona entre 1 y " + options.size() + ".\n\n"
                        + prompt + "\n\n" + formatOptions(),
                ConversationState.valueOf(stateKey));
    }

    private String detailPrompt(MenuOption selected) {
        return switch (stateKey) {
            case "RECHARGE_MENU" -> switch (selected.optionKey()) {
                case "RECHARGE_MY_LINE" -> "Recarga a tu linea. Montos disponibles:";
                case "RECHARGE_OTHER_NUMBER" -> "Ingresa el numero al cual deseas recargar.";
                case "USE_POINTS" -> "Compra con puntos. Puntos disponibles:";
                case "INTERNATIONAL_RECHARGE" -> "Recarga internacional. Paises disponibles:";
                default -> "Detalle: " + selected.displayLabel();
            };
            case "COMPLAINT_MENU" -> "Comentario para: " + selected.displayLabel() + ". Por favor, describe tu caso.";
            case "PERSONAL_INFO_MENU" -> "Seleccionaste: " + selected.displayLabel() + ".";
            case "SUPPORT_MENU" -> "Soporte: " + selected.displayLabel() + ". Instrucciones:";
            default -> "Detalle: " + selected.displayLabel();
        };
    }

    private Outcome handleGlobal(String cmd) {
        return switch (cmd) {
            case "menu" -> Outcome.textOnly(
                    "Hola! Bienvenido.\n\nSelecciona una opcion:\n\n" + formatMain(),
                    ConversationState.MAIN_MENU);
            case "cancelar" -> Outcome.textOnly(
                    "Tu conversacion ha sido cancelada. Escribe 'hola' para comenzar de nuevo.",
                    ConversationState.CANCELLED);
            case "volver" -> Outcome.textOnly(
                    "Regresando al menu principal.\n\nSelecciona una opcion:\n\n" + formatMain(),
                    ConversationState.MAIN_MENU);
            default -> Outcome.textOnly(prompt + "\n\n" + formatOptions(), ConversationState.valueOf(stateKey));
        };
    }

    private static String formatOptions(List<MenuOption> options) {
        return formatOptionsStatic(options);
    }

    private static String formatMain() {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (MenuOption opt : MenuCatalog.mainMenu()) {
            sb.append(i).append(". ").append(opt.displayLabel()).append("\n");
            i++;
        }
        return sb.toString().trim();
    }

    private String formatOptions() {
        return formatOptionsStatic(options);
    }

    private static String formatOptionsStatic(List<MenuOption> options) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (MenuOption opt : options) {
            sb.append(i).append(". ").append(opt.displayLabel()).append("\n");
            i++;
        }
        return sb.toString().trim();
    }
}