package com.botwap.domain.engine.handler;

import com.botwap.domain.engine.InputNormalizer;
import com.botwap.domain.engine.StateHandler;
import com.botwap.domain.model.ConversationState;

/**
 * Handler del Nivel 5: CONFIRMATION_MENU.
 *
 * <p>Opciones:
 * 1. Confirmar  -> FINAL
 * 2. Cambiar paquete -> regresa a PRODUCT_MENU (N3)
 * 3. Hablar con asesor -> FINAL (indicacion de canal)
 * 4. Cancelar -> CANCELLED
 *
 * <p>La respuesta FINAL solo es alcanzable desde este estado (N5).</p>
 */
public final class ConfirmationMenuHandler implements StateHandler {

    @Override
    public Outcome handle(Context ctx) {
        String normalized = InputNormalizer.normalize(ctx.input());

        if (InputNormalizer.isGlobalCommand(normalized)) {
            return handleGlobal(normalized);
        }

        try {
            int option = Integer.parseInt(normalized);
            return switch (option) {
                case 1 -> new Outcome(
                        "Tu solicitud del paquete ha sido confirmada. Un proceso continuara con tu solicitud.",
                        ConversationState.FINAL,
                        null);
                case 2 -> Outcome.textOnly(
                        "Selecciona tu paquete:\n\n1. 5 GB\n2. 10 GB\n3. 20 GB\n4. Ilimitado",
                        ConversationState.PRODUCT_MENU);
                case 3 -> new Outcome(
                        "Has solicitado hablar con un asesor. Un representante se pondra en contacto contigo pronto.",
                        ConversationState.FINAL,
                        null);
                case 4 -> new Outcome(
                        "Tu solicitud ha sido cancelada.",
                        ConversationState.CANCELLED,
                        null);
                default -> Outcome.textOnly(
                        "Opcion no valida. Selecciona 1, 2, 3 o 4.\n\n"
                                + "1. Confirmar\n2. Cambiar paquete\n3. Hablar con un asesor\n4. Cancelar",
                        ConversationState.CONFIRMATION_MENU);
            };
        } catch (NumberFormatException e) {
            return Outcome.textOnly(
                    "Entrada no reconocida. Selecciona 1, 2, 3 o 4.\n\n"
                            + "1. Confirmar\n2. Cambiar paquete\n3. Hablar con un asesor\n4. Cancelar",
                    ConversationState.CONFIRMATION_MENU);
        }
    }

    private Outcome handleGlobal(String cmd) {
        return switch (cmd) {
            case "menu" -> Outcome.textOnly(
                    "Hola! Bienvenido.\n\nSelecciona una opcion:\n\n"
                            + "1. Compra de paquetes\n2. Recargas\n3. Quejas o reclamos\n4. Informacion personal\n5. Soporte tecnico",
                    ConversationState.MAIN_MENU);
            case "cancelar" -> Outcome.textOnly(
                    "Tu conversacion ha sido cancelada. Escribe 'hola' para comenzar de nuevo.",
                    ConversationState.CANCELLED);
            case "volver" -> Outcome.textOnly(
                    "Selecciona tu paquete:\n\n1. 5 GB\n2. 10 GB\n3. 20 GB\n4. Ilimitado",
                    ConversationState.PRODUCT_MENU);
            default -> Outcome.textOnly(
                    "Selecciona 1, 2, 3 o 4.",
                    ConversationState.CONFIRMATION_MENU);
        };
    }
}