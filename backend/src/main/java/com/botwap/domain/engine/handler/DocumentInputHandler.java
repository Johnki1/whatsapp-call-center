package com.botwap.domain.engine.handler;

import com.botwap.domain.engine.DocumentValidator;
import com.botwap.domain.engine.InputNormalizer;
import com.botwap.domain.engine.StateHandler;
import com.botwap.domain.model.ConversationSelection;
import com.botwap.domain.model.ConversationState;
import com.botwap.domain.menu.MenuCatalog;
import com.botwap.domain.menu.MenuOption;

import java.util.List;

public final class DocumentInputHandler implements StateHandler {

    private static final int MAX_ATTEMPTS = 3;

    @Override
    public Outcome handle(Context ctx) {
        String normalized = InputNormalizer.normalize(ctx.input());

        if (InputNormalizer.isGlobalCommand(normalized)) {
            return handleGlobal(normalized);
        }

        DocumentValidator.DocumentType docType = extractDocumentType(ctx.selections());
        if (docType == null) {
            return Outcome.textOnly(
                    "No se encontro el tipo de identificacion.\n\n"
                            + "Selecciona tu tipo de identificacion:\n\n"
                            + formatIdOptions(),
                    ConversationState.IDENTIFICATION_MENU);
        }

        String text = ctx.input() == null ? "" : ctx.input().trim();

        if (docType == DocumentValidator.DocumentType.NEW_CLIENT) {
            ConversationSelection inputSelection = ConversationSelection.unpersisted(
                    4, "DOCUMENT_INPUT", "NEW_CLIENT_DOC",
                    "Cliente nuevo (sin documento)",
                    "{\"documentType\":\"NEW_CLIENT\",\"documentMasked\":\"N/A\"}");
            return new Outcome(buildConfirmation(ctx, docType, null), ConversationState.CONFIRMATION_MENU, inputSelection);
        }

        DocumentValidator.ValidateResult validation = DocumentValidator.validate(docType, text);
        if (!validation.valid()) {
            String attemptsInfo = attemptsWarning(ctx);
            return new Outcome(
                    validation.message() + attemptsInfo + "\n\nIngresa tu numero de documento.",
                    ConversationState.DOCUMENT_INPUT,
                    null);
        }

        ConversationSelection inputSelection = ConversationSelection.unpersisted(
                4, "DOCUMENT_INPUT", "DOCUMENT_SUBMITTED", "Documento recibido",
                "{\"documentType\":\"" + docType.name() + "\",\"documentMasked\":\"" + mask(text) + "\"}");
        return new Outcome(buildConfirmation(ctx, docType, text), ConversationState.CONFIRMATION_MENU, inputSelection);
    }

    private DocumentValidator.DocumentType extractDocumentType(List<ConversationSelection> selections) {
        return selections.stream()
                .filter(s -> s.level() == 4 && "IDENTIFICATION_MENU".equals(s.stateKey()))
                .map(s -> parseDocumentType(s.optionKey()))
                .findFirst()
                .orElse(null);
    }

    private DocumentValidator.DocumentType parseDocumentType(String key) {
        try {
            return DocumentValidator.DocumentType.valueOf(key);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    private String mask(String document) {
        if (document == null || document.length() <= 4) {
            return "*".repeat(document == null ? 0 : document.length());
        }
        String body = document.substring(0, document.length() - 4);
        String visible = document.substring(document.length() - 4);
        return "*".repeat(body.length()) + visible;
    }

    private String buildConfirmation(Context ctx, DocumentValidator.DocumentType docType, String document) {
        StringBuilder sb = new StringBuilder();
        sb.append("Confirma tu solicitud:\n\n");
        sb.append("Servicio: ").append(levelLabel(ctx, 1)).append("\n");
        sb.append("Producto: ").append(levelLabel(ctx, 2)).append("\n");
        String detail = levelLabel(ctx, 3);
        if (detail != null && !detail.isBlank()) {
            sb.append("Paquete: ").append(detail).append("\n");
        }
        sb.append("Tipo de identificacion: ").append(docTypeLabel(docType)).append("\n");
        sb.append("Documento: ").append(document == null ? "N/A" : mask(document)).append("\n\n");
        sb.append("Selecciona:\n\n");
        sb.append("1. Confirmar\n");
        sb.append("2. Cambiar paquete\n");
        sb.append("3. Hablar con un asesor\n");
        sb.append("4. Cancelar");
        return sb.toString();
    }

    /** Etiqueta visible de la seleccion del nivel indicado (o vacio si no existe). */
    private static String levelLabel(Context ctx, int level) {
        return ctx.selections().stream()
                .filter(s -> s.level() == level)
                .map(ConversationSelection::displayLabel)
                .findFirst()
                .orElse("-");
    }

    private String docTypeLabel(DocumentValidator.DocumentType type) {
        return switch (type) {
            case CC -> "Cedula de ciudadania";
            case PASSPORT -> "Pasaporte";
            case NIT -> "NIT";
            case NEW_CLIENT -> "Cliente nuevo";
        };
    }

    private String attemptsWarning(Context ctx) {
        long attempts = ctx.selections().stream()
                .filter(s -> s.level() == 4 && "DOCUMENT_INPUT".equals(s.stateKey()))
                .count();
        long remaining = MAX_ATTEMPTS - attempts;
        if (remaining <= 1) {
            return "\n\n( Ultimo intento: " + remaining + " restante )";
        }
        return "";
    }

    private Outcome handleGlobal(String cmd) {
        return switch (cmd) {
            case "menu" -> Outcome.textOnly(
                    "Hola! Bienvenido.\n\nSelecciona una opcion:\n\n" + formatMainOptions(),
                    ConversationState.MAIN_MENU);
            case "cancelar" -> Outcome.textOnly(
                    "Tu conversacion ha sido cancelada. Escribe 'hola' para comenzar de nuevo.",
                    ConversationState.CANCELLED);
            case "volver" -> Outcome.textOnly(
                    "Selecciona tu tipo de identificacion:\n\n" + formatIdOptions(),
                    ConversationState.IDENTIFICATION_MENU);
            default -> Outcome.textOnly("Ingresa tu numero de documento.", ConversationState.DOCUMENT_INPUT);
        };
    }

    private static String formatIdOptions() {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (MenuOption opt : MenuCatalog.identificationMenu()) {
            sb.append(i).append(". ").append(opt.displayLabel()).append("\n");
            i++;
        }
        return sb.toString().trim();
    }

    private static String formatMainOptions() {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (MenuOption opt : MenuCatalog.mainMenu()) {
            sb.append(i).append(". ").append(opt.displayLabel()).append("\n");
            i++;
        }
        return sb.toString().trim();
    }
}