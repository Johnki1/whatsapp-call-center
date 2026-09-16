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
 * Handler del Nivel 5: confirmación de la solicitud.
 *
 * <p>Acciones (mensaje interactivo nativo; los ids son {@code CONFIRM},
 * {@code CHANGE_PACKAGE}, {@code AGENT} y {@code CANCEL}; por compatibilidad
 * también se aceptan los números 1-4):
 * <ol>
 *   <li>Confirmar → {@code FINAL} con la despedida contextual de la rama.</li>
 *   <li>Cambiar → regresa al Nivel 3 de la rama (paquete u opción del caso).</li>
 *   <li>Hablar con un asesor → {@code HUMAN_AGENT} (handoff humano, silencio).</li>
 *   <li>Cancelar → {@code CANCELLED}.</li>
 * </ol>
 *
 * <p>{@code FINAL}, {@code CANCELLED} y {@code HUMAN_AGENT} son estados
 * terminales: la conversación se cierra y el bot permanece en silencio hasta un
 * reinicio explícito.</p>
 */
public final class ConfirmationMenuHandler implements StateHandler {

    /** Id de la acción de handoff a asesor humano en el menú de confirmación. */
    static final String AGENT_OPTION_ID = "AGENT";

    @Override
    public Outcome handle(Context ctx) {
        String normalized = InputNormalizer.normalize(ctx.input());
        ServiceBranch branch = ServiceBranch.fromSelections(ctx.selections());
        List<MenuOption> actions = MenuCatalog.confirmationMenu(branch == ServiceBranch.PURCHASE);

        if (InputNormalizer.isGlobalCommand(normalized)) {
            return handleGlobal(normalized, branch);
        }

        Optional<MenuOption> selected = InputNormalizer.matchOption(actions, normalized);
        if (selected.isEmpty()) {
            return Outcome.menu(BotCopy.notUnderstood(), ConversationState.CONFIRMATION_MENU, null,
                    InteractiveOption.listOf(actions));
        }

        MenuOption action = selected.get();
        return switch (action.optionKey()) {
            case "CONFIRM" -> new Outcome(
                    BotCopy.success(ctx.selections()), ConversationState.FINAL, null, List.of());
            case "CHANGE_PACKAGE" -> Outcome.menu(
                    BotCopy.detailPrompt(branch, categoryLabel(ctx, branch)),
                    branch.backState(),
                    null,
                    detailOptions(branch));
            case AGENT_OPTION_ID -> new Outcome(
                    BotCopy.humanHandoff(), ConversationState.HUMAN_AGENT, null, List.of());
            case "CANCEL" -> Outcome.textOnly(BotCopy.cancelled(), ConversationState.CANCELLED);
            default -> Outcome.menu(BotCopy.notUnderstood(), ConversationState.CONFIRMATION_MENU,
                    null, InteractiveOption.listOf(actions));
        };
    }

    /** Opciones del Nivel 3 de la rama, para re-presentar el submenú al cambiar. */
    static List<InteractiveOption> detailOptions(ServiceBranch branch) {
        return InteractiveOption.listOf(MenuCatalog.detailOptionsFor(branch.stateKey()));
    }

    /** Categoría (Nivel 2) de la rama activa, para el prompt de regreso al Nivel 3. */
    private static String categoryLabel(Context ctx, ServiceBranch branch) {
        return ServiceBranch.labelFor(ctx.selections(), 2, branch.stateKey()).orElse(null);
    }

    private Outcome handleGlobal(String cmd, ServiceBranch branch) {
        return switch (cmd) {
            case "cancelar" -> Outcome.textOnly(BotCopy.cancelled(), ConversationState.CANCELLED);
            case "volver" -> Outcome.menu(
                    BotCopy.documentTypeMissing(), ConversationState.IDENTIFICATION_MENU, null,
                    InteractiveOption.listOf(MenuCatalog.identificationMenu()));
            default -> Outcome.menu(BotCopy.backToMain(), ConversationState.MAIN_MENU, null,
                    InteractiveOption.listOf(MenuCatalog.mainMenu()));
        };
    }
}
