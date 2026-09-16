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
 * Handler genérico de los menús de categoría (Nivel 2) de las cinco ramas.
 *
 * <p>Presenta el submenú correspondiente como mensaje interactivo nativo (lista
 * para 4 opciones) con ids estables; también acepta la selección por número.
 * El comportamiento es idéntico para todas las ramas: registrar la selección de
 * Nivel 2 y avanzar al Nivel 3. La única diferencia entre ramas —el destino de
 * Nivel 3 y los textos— se deriva de {@link ServiceBranch}.</p>
 */
public final class CategoryMenuHandler implements StateHandler {

    private final ServiceBranch branch;

    public CategoryMenuHandler(ServiceBranch branch) {
        this.branch = branch;
    }

    @Override
    public Outcome handle(Context ctx) {
        String normalized = InputNormalizer.normalize(ctx.input());

        if (InputNormalizer.isGlobalCommand(normalized)) {
            return handleGlobal(normalized);
        }

        List<MenuOption> options = MenuCatalog.optionsFor(branch.stateKey());
        Optional<MenuOption> selected = InputNormalizer.matchOption(options, normalized);
        if (selected.isEmpty()) {
            return invalidInput(options);
        }

        MenuOption chosen = selected.get();
        return Outcome.menu(
                BotCopy.detailPrompt(branch, chosen.label()),
                nextState(),
                ConversationSelection.unpersisted(2, branch.stateKey(), chosen.optionKey(),
                        chosen.label(), "{}"),
                InteractiveOption.listOf(MenuCatalog.detailOptionsFor(branch.stateKey())));
    }

    /** Rama de compra → catálogo de paquetes; resto → submenú de detalle. */
    private ConversationState nextState() {
        return branch == ServiceBranch.PURCHASE
                ? ConversationState.PRODUCT_MENU
                : ConversationState.DETAIL_MENU;
    }

    private Outcome invalidInput(List<MenuOption> options) {
        return Outcome.menu(BotCopy.notUnderstood(), currentState(), null,
                InteractiveOption.listOf(options));
    }

    private ConversationState currentState() {
        return ConversationState.valueOf(branch.stateKey());
    }

    private Outcome handleGlobal(String cmd) {
        return switch (cmd) {
            case "cancelar" -> Outcome.textOnly(BotCopy.cancelled(), ConversationState.CANCELLED);
            default -> Outcome.menu(BotCopy.backToMain(), ConversationState.MAIN_MENU, null,
                    InteractiveOption.listOf(MenuCatalog.mainMenu()));
        };
    }
}
