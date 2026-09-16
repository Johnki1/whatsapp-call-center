package com.botwap.domain.engine.handler;

import com.botwap.domain.engine.BotCopy;
import com.botwap.domain.engine.InputNormalizer;
import com.botwap.domain.engine.ServiceBranch;
import com.botwap.domain.engine.StateHandler;
import com.botwap.domain.menu.InteractiveOption;
import com.botwap.domain.menu.MenuCatalog;
import com.botwap.domain.menu.MenuOption;
import com.botwap.domain.model.ConversationSelection;
import com.botwap.domain.model.ConversationState;

import java.util.List;
import java.util.Optional;

/**
 * Handler del Nivel 3 de la rama de compra: selección del paquete de datos
 * (mensaje interactivo nativo con los paquetes del catálogo).
 *
 * <p>Al elegir un paquete se registra la selección de Nivel 3 y el flujo entra
 * al primer paso de la identificación (Nivel 4): el nombre del usuario.</p>
 *
 * <p>La opción 3 aquí (20 GB) NO significa «Quejas»: cada estado interpreta su
 * entrada de forma aislada.</p>
 */
public final class ProductMenuHandler implements StateHandler {

    private static final ServiceBranch BRANCH = ServiceBranch.PURCHASE;

    @Override
    public Outcome handle(Context ctx) {
        String normalized = InputNormalizer.normalize(ctx.input());

        if (InputNormalizer.isGlobalCommand(normalized)) {
            return handleGlobal(normalized);
        }

        List<MenuOption> options = MenuCatalog.productMenu();
        Optional<MenuOption> selected = InputNormalizer.matchOption(options, normalized);
        if (selected.isEmpty()) {
            return Outcome.menu(BotCopy.notUnderstood(), ConversationState.PRODUCT_MENU, null,
                    InteractiveOption.listOf(options));
        }

        MenuOption chosen = selected.get();
        return new Outcome(
                BotCopy.beforeName(BRANCH, chosen.label()),
                ConversationState.NAME_INPUT,
                ConversationSelection.unpersisted(3, "PRODUCT_MENU", chosen.optionKey(),
                        chosen.label(), "{}"),
                List.of());
    }

    private Outcome handleGlobal(String cmd) {
        return switch (cmd) {
            case "cancelar" -> Outcome.textOnly(BotCopy.cancelled(), ConversationState.CANCELLED);
            case "volver" -> Outcome.menu(
                    BotCopy.categoryPrompt(BRANCH), ConversationState.PURCHASE_MENU, null,
                    InteractiveOption.listOf(MenuCatalog.optionsFor(BRANCH.stateKey())));
            default -> Outcome.menu(BotCopy.backToMain(), ConversationState.MAIN_MENU, null,
                    InteractiveOption.listOf(MenuCatalog.mainMenu()));
        };
    }
}
