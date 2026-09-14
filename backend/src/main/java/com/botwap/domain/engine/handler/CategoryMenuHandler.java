package com.botwap.domain.engine.handler;

import com.botwap.domain.engine.InputNormalizer;
import com.botwap.domain.engine.StateHandler;
import com.botwap.domain.model.ConversationSelection;
import com.botwap.domain.model.ConversationState;
import com.botwap.domain.menu.MenuOption;
import com.botwap.domain.menu.MenuCatalog;

import java.util.List;
import java.util.function.Function;

/**
 * Handler generico para los Menus de categoria (Nivel 2).
 *
 * <p>Parametrizado por:
 * - stateKey origen (MAIN_MENU) del que se vino;
 * - opciones del menu N2;
 * - funcion que mapea la opcion elegida al estado N3 destino.</p>
 */
public final class CategoryMenuHandler implements StateHandler {

    private final String originStateKey;
    private final String stateKey;
    private final List<MenuOption> options;
    private final String prompt;
    private final Function<MenuOption, ConversationState> nextStateMapper;

    public CategoryMenuHandler(String originStateKey, String stateKey,
                               List<MenuOption> options, String prompt,
                               Function<MenuOption, ConversationState> nextStateMapper) {
        this.originStateKey = originStateKey;
        this.stateKey = stateKey;
        this.options = options;
        this.prompt = prompt;
        this.nextStateMapper = nextStateMapper;
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
                return Outcome.textOnly(
                        "Opcion no valida. Selecciona entre 1 y " + options.size() + ".\n\n"
                                + prompt + "\n\n" + formatOptions(options),
                        stateToState());
            }
            MenuOption selected = options.get(option - 1);
            ConversationState next = nextStateMapper.apply(selected);
            return new Outcome(
                    prompt + "\n\n" + formatOptions(nextLevelOptions(next)),
                    next,
                    ConversationSelection.unpersisted(2, stateKey, selected.optionKey(), selected.displayLabel(), "{}"));
        } catch (NumberFormatException e) {
            return Outcome.textOnly(
                    "Entrada no reconocida. Selecciona entre 1 y " + options.size() + ".\n\n"
                            + prompt + "\n\n" + formatOptions(options),
                    stateToState());
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
                    "Regresando al menu principal.\n\nSelecciona una opcion:\n\n"
                            + formatOptions(MenuCatalog.mainMenu()),
                    ConversationState.MAIN_MENU);
            default -> Outcome.textOnly(
                    "Selecciona una opcion valida.\n\n" + prompt + "\n\n" + formatOptions(options),
                    stateToState());
        };
    }

    private ConversationState stateToState() {
        return ConversationState.valueOf(stateKey);
    }

    private List<MenuOption> nextLevelOptions(ConversationState next) {
        return switch (next) {
            case ConversationState.PRODUCT_MENU -> MenuCatalog.productMenu();
            case ConversationState.DETAIL_MENU -> {
                // Determina qué submenú de detalle mostrar basado en el stateKey actual
                yield switch (stateKey) {
                    case "RECHARGE_MENU" -> MenuCatalog.rechargeDetailMenu();
                    case "COMPLAINT_MENU" -> MenuCatalog.complaintDetailMenu();
                    case "PERSONAL_INFO_MENU" -> MenuCatalog.personalInfoDetailMenu();
                    case "SUPPORT_MENU" -> MenuCatalog.supportDetailMenu();
                    default -> MenuCatalog.productMenu();
                };
            }
            default -> MenuCatalog.productMenu();
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