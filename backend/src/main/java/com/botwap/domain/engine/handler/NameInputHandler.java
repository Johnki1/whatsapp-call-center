package com.botwap.domain.engine.handler;

import com.botwap.domain.engine.BotCopy;
import com.botwap.domain.engine.InputNormalizer;
import com.botwap.domain.engine.NameValidator;
import com.botwap.domain.engine.ServiceBranch;
import com.botwap.domain.engine.StateHandler;
import com.botwap.domain.engine.ValidationResult;
import com.botwap.domain.menu.InteractiveOption;
import com.botwap.domain.menu.MenuCatalog;
import com.botwap.domain.model.ConversationSelection;
import com.botwap.domain.model.ConversationState;

/**
 * Handler del Nivel 4: captura del nombre completo (primer paso de la
 * identificación).
 *
 * <p>Orden del Nivel 4: <strong>Nombre → Tipo de identificación → Número de
 * documento</strong>. El nombre se persiste como selección
 * ({@code level=4, stateKey=NAME_INPUT}) para que el resumen de confirmación y
 * la despedida puedan personalizarse: el valor limpio se guarda en
 * {@code display_label} y el detalle estructurado en {@code metadata}. Si el
 * usuario ya tiene nombre de perfil de WhatsApp, se le sugiere como valor.</p>
 */
public final class NameInputHandler implements StateHandler {

    /** Clave estable de la selección del nombre (nivel 4). */
    static final String STATE_KEY = "NAME_INPUT";

    @Override
    public Outcome handle(Context ctx) {
        String normalized = InputNormalizer.normalize(ctx.input());
        ServiceBranch branch = ServiceBranch.fromSelections(ctx.selections());

        if (InputNormalizer.isGlobalCommand(normalized)) {
            return handleGlobal(normalized, ctx, branch);
        }

        ValidationResult validation = NameValidator.validate(ctx.input());
        if (!validation.valid()) {
            return Outcome.textOnly(
                    validation.message() + "\n\n" + namePrompt(ctx),
                    ConversationState.NAME_INPUT);
        }

        String fullName = NameValidator.normalize(ctx.input());
        return Outcome.menu(
                BotCopy.identificationPrompt(fullName),
                ConversationState.IDENTIFICATION_MENU,
                ConversationSelection.unpersisted(4, STATE_KEY, "NAME_SUBMITTED", fullName,
                        metadata(fullName)),
                InteractiveOption.listOf(MenuCatalog.identificationMenu()));
    }

    /** Prompt del nombre: si hay perfil de WhatsApp, se sugiere como valor. */
    private static String namePrompt(Context ctx) {
        String base = BotCopy.namePrompt();
        if (ctx.userName() == null || ctx.userName().isBlank()) {
            return base;
        }
        return base + "\n\n_Si tu nombre es *" + InputNormalizer.normalize(ctx.userName())
                + "*, puedes confirmarlo escribiéndolo tal cual._";
    }

    private static String metadata(String fullName) {
        return "{\"fullName\":\"" + escape(fullName) + "\"}";
    }

    /**
     * Escapa comillas y barras invertidas del JSON de metadatos.
     *
     * <p>{@link NameValidator} ya rechaza ambos caracteres; el escapado es una
     * segunda barrera para no depender de esa validación (defensa en profundidad).</p>
     */
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Outcome handleGlobal(String cmd, Context ctx, ServiceBranch branch) {
        return switch (cmd) {
            case "cancelar" -> Outcome.textOnly(BotCopy.cancelled(), ConversationState.CANCELLED);
            case "volver" -> Outcome.menu(
                    BotCopy.detailPrompt(branch,
                            ServiceBranch.labelFor(ctx.selections(), 2, branch.stateKey()).orElse(null)),
                    branch.backState(),
                    null,
                    InteractiveOption.listOf(MenuCatalog.detailOptionsFor(branch.stateKey())));
            default -> Outcome.menu(BotCopy.backToMain(), ConversationState.MAIN_MENU, null,
                    InteractiveOption.listOf(MenuCatalog.mainMenu()));
        };
    }
}
