package com.botwap.domain.engine.handler;

import com.botwap.domain.engine.BotCopy;
import com.botwap.domain.engine.DocumentValidator;
import com.botwap.domain.engine.InputNormalizer;
import com.botwap.domain.engine.ServiceBranch;
import com.botwap.domain.engine.StateHandler;
import com.botwap.domain.engine.ValidationResult;
import com.botwap.domain.menu.InteractiveOption;
import com.botwap.domain.menu.MenuCatalog;
import com.botwap.domain.menu.MenuOption;
import com.botwap.domain.model.ConversationSelection;
import com.botwap.domain.model.ConversationState;

import java.util.List;
import java.util.Optional;

/**
 * Handler del Nivel 4: selección del tipo de documento de identificación
 * (segundo paso de la identificación, después del nombre).
 *
 * <p>El menú se presenta como mensaje interactivo nativo con los ids estables
 * del catálogo ({@code CC}, {@code PASSPORT}, {@code NIT}, {@code NEW_CLIENT});
 * también acepta la selección por número. Al elegir el tipo se avanza a
 * {@code DOCUMENT_INPUT} con la selección de nivel 4 registrada.</p>
 */
public final class IdentificationMenuHandler implements StateHandler {

    /** Clave estable de la selección del tipo de documento (nivel 4). */
    static final String STATE_KEY = "IDENTIFICATION_MENU";

    @Override
    public Outcome handle(Context ctx) {
        String normalized = InputNormalizer.normalize(ctx.input());

        if (InputNormalizer.isGlobalCommand(normalized)) {
            return handleGlobal(normalized);
        }

        List<MenuOption> options = MenuCatalog.identificationMenu();
        Optional<MenuOption> selected = InputNormalizer.matchOption(options, normalized);
        if (selected.isEmpty()) {
            return menuOutcome(BotCopy.notUnderstood());
        }

        MenuOption chosen = selected.get();
        DocumentValidator.DocumentType documentType =
                DocumentValidator.fromOptionKey(chosen.optionKey());
        String metadata = "{\"documentType\":\"" + chosen.optionKey() + "\"}";

        return new Outcome(
                BotCopy.documentPrompt(documentType),
                ConversationState.DOCUMENT_INPUT,
                ConversationSelection.unpersisted(4, STATE_KEY, chosen.optionKey(),
                        chosen.label(), metadata),
                List.of());
    }

    /** Re-presenta el menú de identificación como mensaje interactivo. */
    private Outcome menuOutcome(String text) {
        return Outcome.menu(text, ConversationState.IDENTIFICATION_MENU, null,
                InteractiveOption.listOf(MenuCatalog.identificationMenu()));
    }

    private Outcome handleGlobal(String cmd) {
        return switch (cmd) {
            case "cancelar" -> Outcome.textOnly(BotCopy.cancelled(), ConversationState.CANCELLED);
            case "volver" -> Outcome.textOnly(BotCopy.namePrompt(), ConversationState.NAME_INPUT);
            default -> menuOutcome(BotCopy.backToMain());
        };
    }
}
