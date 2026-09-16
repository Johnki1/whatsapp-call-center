package com.botwap.domain.engine.handler;

import com.botwap.domain.engine.BotCopy;
import com.botwap.domain.engine.DocumentValidator;
import com.botwap.domain.engine.InputNormalizer;
import com.botwap.domain.engine.StateHandler;
import com.botwap.domain.engine.ValidationResult;
import com.botwap.domain.menu.InteractiveOption;
import com.botwap.domain.menu.MenuCatalog;
import com.botwap.domain.model.ConversationSelection;
import com.botwap.domain.model.ConversationState;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler del Nivel 4: ingreso del número de documento (tercer paso de la
 * identificación) y construcción del resumen de confirmación (Nivel 5).
 *
 * <p>El resumen se genera con {@link BotCopy#confirmationPrompt(List)} a partir
 * de las selecciones persistidas más la selección del documento que se registra
 * en este mismo paso, de modo que es contextual a la rama (Paquete / Motivo /
 * Monto…) e incluye nombre y documento enmascarado. Las acciones de
 * confirmación viajan como menú interactivo desde
 * {@link ConfirmationMenuHandler}.</p>
 */
public final class DocumentInputHandler implements StateHandler {

    /** Clave estable de la selección del número de documento (nivel 4). */
    static final String STATE_KEY = "DOCUMENT_INPUT";

    private static final int MAX_ATTEMPTS = 3;

    @Override
    public Outcome handle(Context ctx) {
        String normalized = InputNormalizer.normalize(ctx.input());

        if (InputNormalizer.isGlobalCommand(normalized)) {
            return handleGlobal(normalized);
        }

        DocumentValidator.DocumentType documentType = extractDocumentType(ctx.selections());
        if (documentType == null) {
            return identificationMenuOutcome();
        }

        if (documentType == DocumentValidator.DocumentType.NEW_CLIENT) {
            ConversationSelection inputSelection = ConversationSelection.unpersisted(4, STATE_KEY,
                    "NEW_CLIENT_DOC", "N/A (cliente nuevo)",
                    "{\"documentType\":\"NEW_CLIENT\",\"documentMasked\":\"N/A\"}");
            return new Outcome(
                    BotCopy.confirmationPrompt(withSelection(ctx.selections(), inputSelection)),
                    ConversationState.CONFIRMATION_MENU,
                    inputSelection,
                    confirmationOptions());
        }

        String document = ctx.input() == null ? "" : ctx.input().trim();
        ValidationResult validation = DocumentValidator.validate(documentType, document);
        if (!validation.valid()) {
            return Outcome.textOnly(
                    BotCopy.documentRetry(validation.message(), documentType, attemptsRemaining(ctx)),
                    ConversationState.DOCUMENT_INPUT);
        }

        String masked = mask(document);
        ConversationSelection inputSelection = ConversationSelection.unpersisted(4, STATE_KEY,
                "DOCUMENT_SUBMITTED", masked,
                "{\"documentType\":\"" + documentType.name() + "\",\"documentMasked\":\"" + masked + "\"}");
        return new Outcome(
                BotCopy.confirmationPrompt(withSelection(ctx.selections(), inputSelection)),
                ConversationState.CONFIRMATION_MENU,
                inputSelection,
                confirmationOptions());
    }

    /** Opciones de confirmación (se reutilizan al re-presentar el resumen). */
    static List<InteractiveOption> confirmationOptions() {
        return InteractiveOption.listOf(MenuCatalog.confirmationMenu());
    }

    /** Re-presenta el menú de identificación (falta el tipo de documento). */
    private static Outcome identificationMenuOutcome() {
        return Outcome.menu(
                BotCopy.documentTypeMissing(),
                ConversationState.IDENTIFICATION_MENU,
                null,
                InteractiveOption.listOf(MenuCatalog.identificationMenu()));
    }

    /** Tipo de documento elegido (selección de nivel 4 registrada en el menú previo). */
    private DocumentValidator.DocumentType extractDocumentType(List<ConversationSelection> selections) {
        return selections.stream()
                .filter(s -> s.level() == 4 && "IDENTIFICATION_MENU".equals(s.stateKey()))
                .map(s -> DocumentValidator.fromOptionKey(s.optionKey()))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /** Copia de las selecciones persistidas + la que se está registrando ahora. */
    private static List<ConversationSelection> withSelection(List<ConversationSelection> selections,
                                                             ConversationSelection newSelection) {
        List<ConversationSelection> all = new ArrayList<>(selections);
        all.add(newSelection);
        return all;
    }

    /** Enmascara el documento dejando visibles los últimos 4 caracteres. */
    static String mask(String document) {
        if (document == null || document.isEmpty()) {
            return "";
        }
        if (document.length() <= 4) {
            return "*".repeat(document.length());
        }
        String body = document.substring(0, document.length() - 4);
        String visible = document.substring(document.length() - 4);
        return "*".repeat(body.length()) + visible;
    }

    /** Intentos restantes de documento (cada intento queda registrado como selección). */
    private static long attemptsRemaining(Context ctx) {
        long attempts = ctx.selections().stream()
                .filter(s -> s.level() == 4 && STATE_KEY.equals(s.stateKey()))
                .count();
        return Math.max(1, MAX_ATTEMPTS - attempts);
    }

    private Outcome handleGlobal(String cmd) {
        return switch (cmd) {
            case "cancelar" -> Outcome.textOnly(BotCopy.cancelled(), ConversationState.CANCELLED);
            case "menu" -> Outcome.menu(BotCopy.backToMain(), ConversationState.MAIN_MENU, null,
                    InteractiveOption.listOf(MenuCatalog.mainMenu()));
            default -> identificationMenuOutcome();
        };
    }
}
