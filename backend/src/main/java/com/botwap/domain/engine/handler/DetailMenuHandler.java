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
 * Handler del Nivel 3: submenú de detalle de las ramas Recargas, Quejas,
 * Información personal y Soporte (mensaje interactivo nativo con las opciones
 * del submenú de la rama activa).
 *
 * <p>La rama activa se deriva de la selección de Nivel 1 persistida (fuente de
 * verdad en PostgreSQL) y el {@code stateKey} del submenú de la propia rama: al
 * resolver por clave explícita en lugar de «nivel», las selecciones de ramas
 * previas que sigan almacenadas no contaminan el contexto y el menú mostrado es
 * siempre el correcto.</p>
 *
 * <p>Al elegir una opción se registra la selección de Nivel 3 y el flujo entra
 * al primer paso de la identificación (Nivel 4): el nombre del usuario.</p>
 */
public final class DetailMenuHandler implements StateHandler {

    @Override
    public Outcome handle(Context ctx) {
        String normalized = InputNormalizer.normalize(ctx.input());
        ServiceBranch branch = ServiceBranch.fromSelections(ctx.selections());

        if (InputNormalizer.isGlobalCommand(normalized)) {
            return handleGlobal(normalized, ctx, branch);
        }

        List<MenuOption> options = MenuCatalog.detailOptionsFor(branch.stateKey());
        if (options.isEmpty()) {
            return Outcome.menu(BotCopy.backToMain(), ConversationState.MAIN_MENU, null,
                    InteractiveOption.listOf(MenuCatalog.mainMenu()));
        }

        Optional<MenuOption> selected = InputNormalizer.matchOption(options, normalized);
        if (selected.isEmpty()) {
            return Outcome.menu(BotCopy.notUnderstood(), ConversationState.DETAIL_MENU, null,
                    InteractiveOption.listOf(options));
        }

        MenuOption chosen = selected.get();
        return new Outcome(
                BotCopy.beforeName(branch, chosen.label()),
                ConversationState.NAME_INPUT,
                ConversationSelection.unpersisted(3, branch.stateKey(), chosen.optionKey(),
                        chosen.label(), "{}"),
                List.of());
    }

    private Outcome handleGlobal(String cmd, Context ctx, ServiceBranch branch) {
        return switch (cmd) {
            case "cancelar" -> Outcome.textOnly(BotCopy.cancelled(), ConversationState.CANCELLED);
            case "volver" -> Outcome.menu(
                    BotCopy.categoryPrompt(branch),
                    ConversationState.valueOf(branch.stateKey()),
                    null,
                    InteractiveOption.listOf(MenuCatalog.optionsFor(branch.stateKey())));
            default -> Outcome.menu(BotCopy.backToMain(), ConversationState.MAIN_MENU, null,
                    InteractiveOption.listOf(MenuCatalog.mainMenu()));
        };
    }
}
