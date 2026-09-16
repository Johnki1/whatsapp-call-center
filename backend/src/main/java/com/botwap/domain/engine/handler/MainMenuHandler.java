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
 * Handler del Nivel 1: menú principal.
 *
 * <p>Presenta el menú como <strong>mensaje interactivo nativo tipo lista</strong>
 * (más de 3 opciones) con ids estables ({@code PURCHASE}, {@code RECHARGE}…).
 * Por compatibilidad también acepta la selección por número ({@code 1}…{@code 5}).
 * Saludos → bienvenida personalizada; comandos globales según el comando;
 * entradas inválidas → re-prompt sin cambiar de estado.</p>
 */
public final class MainMenuHandler implements StateHandler {

    @Override
    public Outcome handle(Context ctx) {
        String normalized = InputNormalizer.normalize(ctx.input());

        if (InputNormalizer.isGlobalCommand(normalized)) {
            return handleGlobal(normalized);
        }
        if (InputNormalizer.isHello(normalized)) {
            return menuOutcome(BotCopy.welcome(ctx.userName()));
        }

        List<MenuOption> options = MenuCatalog.mainMenu();
        Optional<MenuOption> selected = InputNormalizer.matchOption(options, normalized);
        if (selected.isEmpty()) {
            return menuOutcome(BotCopy.notUnderstood());
        }
        return transitionTo(selected.get());
    }

    private Outcome transitionTo(MenuOption selected) {
        ServiceBranch branch = ServiceBranch.fromOptionKey(selected.optionKey());
        return Outcome.menu(
                BotCopy.categoryPrompt(branch),
                ConversationState.valueOf(branch.stateKey()),
                ConversationSelection.unpersisted(1, "MAIN_MENU", selected.optionKey(),
                        selected.label(), "{}"),
                branchOptions(branch));
    }

    private Outcome menuOutcome(String text) {
        return Outcome.menu(text, ConversationState.MAIN_MENU, null,
                InteractiveOption.listOf(MenuCatalog.mainMenu()));
    }

    /** Opciones del Nivel 2 correspondientes a la rama elegida. */
    static List<InteractiveOption> branchOptions(ServiceBranch branch) {
        return InteractiveOption.listOf(MenuCatalog.optionsFor(branch.stateKey()));
    }

    private Outcome handleGlobal(String cmd) {
        return switch (cmd) {
            case "menu" -> Outcome.menu(BotCopy.backToMain(), ConversationState.MAIN_MENU, null,
                    InteractiveOption.listOf(MenuCatalog.mainMenu()));
            case "cancelar" -> Outcome.textOnly(BotCopy.cancelled(), ConversationState.CANCELLED);
            default -> Outcome.menu(
                    "🔙 Ya estás en el *menú principal*.\n\nElige una opción 👇",
                    ConversationState.MAIN_MENU, null, InteractiveOption.listOf(MenuCatalog.mainMenu()));
        };
    }
}
