package com.botwap.domain.engine;

import com.botwap.domain.model.ConversationSelection;

import java.util.List;
import java.util.Optional;

/**
 * Copywriting centralizado del bot: tono amable, moderno y profesional, con
 * formato nativo de WhatsApp ({@code *negrita*}, {@code _cursiva_}) y emojis
 * coherentes.
 *
 * <p>Desde la Fase V2 los menús NO se renderizan como texto numerado: cada
 * prompt aporta solo la prosa del mensaje y las opciones viajan como menú
 * interactivo nativo de Meta (botones o lista), con ids estables que el motor
 * reconoce al recibirlos de vuelta.</p>
 */
public final class BotCopy {

    /** Pie de página estándar de los mensajes interactivos. */
    public static final String FOOTER = "🤖 BotWap · Asistente virtual";

    /** Texto del botón CTA de los mensajes tipo lista. */
    public static final String LIST_BUTTON_LABEL = "Ver opciones";

    private BotCopy() {
    }

    // ── Navegación global ────────────────────────────────────────────────

    /** Bienvenida personalizada con el nombre de perfil de WhatsApp. */
    public static String welcome(String profileName) {
        String greeting = (profileName == null || profileName.isBlank())
                ? "👋 *¡Hola! Soy BotWap*"
                : "👋 *¡Hola " + profileName + "!*";
        return greeting + "\n\n"
                + "Soy tu asistente virtual de servicio al cliente y estoy aquí para ayudarte. 🚀\n\n"
                + "Elige una opción del menú para continuar 👇";
    }

    /** Regreso al menú principal. */
    public static String backToMain() {
        return "🔙 *Volviendo al menú principal*\n\n"
                + "Elige una opción del menú para continuar 👇";
    }

    /** Confirmación de cancelación (comando {@code cancelar}). */
    public static String cancelled() {
        return "👋 *Conversación cancelada*\n\n"
                + "No se radicó ninguna solicitud. Escribe *hola* cuando quieras comenzar de nuevo. 🙂";
    }

    /** Entrada no reconocida: re-prompt sin cambiar de estado (menú reenviado). */
    public static String notUnderstood() {
        return "🤔 No logré entender tu mensaje.\n\n"
                + "Puedes elegir una opción del menú o escribir con tus palabras lo que necesitas.";
    }

    /** Opción inexistente: re-prompt sin cambiar de estado (menú reenviado). */
    public static String invalidOption(int min, int max) {
        return "🤔 Esa opción no existe. Escoge un número entre *" + min + "* y *" + max + "* "
                + "o toca una de las opciones del menú.";
    }

    // ── Niveles 2 y 3: navegación de la rama ─────────────────────────────

    /** Prosa del menú de categorías (Nivel 2) de la rama elegida. */
    public static String categoryPrompt(ServiceBranch branch) {
        return branch.emoji() + " *" + branch.displayName() + "*\n\n" + categoryQuestion(branch);
    }

    /** Prosa del submenú de detalle (Nivel 3), con la categoría ya elegida. */
    public static String detailPrompt(ServiceBranch branch, String categoryLabel) {
        String header = categoryLabel == null || categoryLabel.isBlank()
                ? branch.emoji() + " *" + branch.displayName() + "*"
                : branch.emoji() + " *" + categoryLabel + "*";
        return header + "\n\n" + detailQuestion(branch);
    }

    /** Acuse de la selección de Nivel 3 + primer paso de la identificación (nombre). */
    public static String beforeName(ServiceBranch branch, String selectedLabel) {
        return detailEmoji(branch) + " *" + selectedLabel + "*\n\n"
                + "Perfecto, registramos tu " + branch.detailLabel().toLowerCase() + " ✅\n\n"
                + "Para continuar necesitamos identificarte.\n\n"
                + namePrompt();
    }

    private static String categoryQuestion(ServiceBranch branch) {
        return switch (branch) {
            case PURCHASE -> "¿Qué tipo de paquete buscas?";
            case RECHARGE -> "¿Qué quieres hacer?";
            case COMPLAINT -> "¿Sobre qué quieres radicar tu reclamo?";
            case PERSONAL_INFO -> "¿Qué información necesitas?";
            case SUPPORT -> "¿Con qué necesitas ayuda?";
            case UNKNOWN -> "Elige una opción:";
        };
    }

    private static String detailQuestion(ServiceBranch branch) {
        return switch (branch) {
            case PURCHASE -> "¿Qué paquete quieres activar?";
            case RECHARGE -> "¿Qué quieres recargar?";
            case COMPLAINT -> "Cuéntanos el motivo de tu caso:";
            case PERSONAL_INFO -> "¿Qué detalle necesitas?";
            case SUPPORT -> "¿Qué tipo de ayuda necesitas?";
            case UNKNOWN -> "Elige una opción:";
        };
    }

    // ── Nivel 4: identificación (nombre → tipo → número) ─────────────────

    /** Solicitud del nombre completo (primer paso de la identificación). */
    public static String namePrompt() {
        return "👤 *Cuéntame tu nombre completo*\n\n"
                + "Con él personalizamos tu solicitud.\n\n"
                + "_Ejemplo: Juan Pérez_";
    }

    /** Acuse del nombre y solicitud del tipo de identificación. */
    public static String identificationPrompt(String fullName) {
        return "🙌 ¡Gracias, *" + fullName + "*!\n\n"
                + "Ahora elige tu tipo de identificación 👇";
    }

    /** Prompt del número de documento según el tipo elegido. */
    public static String documentPrompt(DocumentValidator.DocumentType documentType) {
        return switch (documentType) {
            case CC -> "💳 *Cédula de ciudadanía*\n\n"
                    + "Escribe tu número de documento (entre *5 y 10 dígitos*, sin puntos ni espacios).";
            case NIT -> "💳 *NIT*\n\n"
                    + "Escribe el número de NIT (entre *8 y 12 dígitos*, sin puntos ni espacios).";
            case PASSPORT -> "🛂 *Pasaporte*\n\n"
                    + "Escribe tu número de pasaporte (entre *5 y 10* letras y/o dígitos).";
            case NEW_CLIENT -> "🎉 *¡Bienvenido como cliente nuevo!*\n\n"
                    + "No necesitas documento de identidad para continuar.\n\n"
                    + "Escribe *continuar* y preparo el resumen de tu solicitud.";
        };
    }

    /** Re-prompt del documento con el motivo del rechazo y los intentos restantes. */
    public static String documentRetry(String reason, DocumentValidator.DocumentType documentType,
                                       long attemptsRemaining) {
        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ ").append(reason).append('\n');
        if (attemptsRemaining <= 1) {
            sb.append("\n_Último intento disponible._");
        }
        sb.append("\n\n").append(documentPrompt(documentType));
        return sb.toString();
    }

    /** Tipo de documento no encontrado en el contexto: se vuelve al menú de identificación. */
    public static String documentTypeMissing() {
        return "🔄 Necesito que elijas primero tu tipo de identificación 👇";
    }

    // ── Nivel 5: confirmación ────────────────────────────────────────────

    /**
     * Resumen contextual de la solicitud (prosa; las acciones viajan como menú
     * interactivo).
     *
     * <p>La etiqueta de la selección de Nivel 3 depende de la rama: en una compra
     * es «Paquete», en un reclamo es «Motivo», en una recarga es «Monto», etc.
     * Incluye el nombre y el documento recién capturados.</p>
     */
    public static String confirmationPrompt(List<ConversationSelection> selections) {
        ServiceBranch branch = ServiceBranch.fromSelections(selections);
        String service = labelAt(selections, 1, "MAIN_MENU").orElse(branch.displayName());
        String category = labelAt(selections, 2, branch.stateKey()).orElse(null);
        String detail = labelAt(selections, 3, detailStateKey(branch)).orElse(null);

        StringBuilder sb = new StringBuilder();
        sb.append("🧾 *Resumen de tu solicitud*\n\n");
        sb.append("👤 *Usuario:* ").append(userDescriptor(selections)).append('\n');
        sb.append(branch.emoji()).append(" *Servicio:* ").append(service).append('\n');
        if (category != null) {
            sb.append("📂 *Categoría:* ").append(category).append('\n');
        }
        if (detail != null) {
            sb.append(detailEmoji(branch)).append(" *").append(branch.detailLabel()).append(":* ")
                    .append(detail).append('\n');
        }
        sb.append("\n¿Confirmas que los datos son correctos?");
        return sb.toString();
    }

    /** Despedida de éxito contextual a la rama, con resumen de lo radicado. */
    public static String success(List<ConversationSelection> selections) {
        ServiceBranch branch = ServiceBranch.fromSelections(selections);
        String service = labelAt(selections, 1, "MAIN_MENU").orElse(branch.displayName());
        String detail = labelAt(selections, 3, detailStateKey(branch)).orElse(null);

        StringBuilder sb = new StringBuilder();
        sb.append(branch.successMessage()).append("\n\n");
        sb.append(branch.emoji()).append(" *Servicio:* ").append(service).append('\n');
        if (detail != null) {
            sb.append(detailEmoji(branch)).append(" *").append(branch.detailLabel()).append(":* ")
                    .append(detail).append('\n');
        }
        sb.append("👤 *Usuario:* ").append(userDescriptor(selections)).append("\n\n");
        sb.append(branch.successHint()).append("\n\n");
        sb.append("Escribe *hola* cuando quieras iniciar una nueva solicitud. 👋");
        return sb.toString();
    }

    /**
     * Mensaje de transición al handoff humano ({@code HUMAN_AGENT}).
     *
     * <p>Después de este mensaje el bot permanece en silencio: la conversación
     * la retoma un asesor humano por el mismo canal.</p>
     */
    /**
     * Mensaje de transición al handoff humano ({@code HUMAN_AGENT}) — Fase V2 actualización Fase 4.
     *
     * <p>Después de este mensaje el bot permanece en silencio: la conversación
     * la retoma un asesor humano por el mismo canal. Incluye la nota {@code INICIO}
     * para cancelar la espera y volver al menú principal.</p>
     */
    public static String humanHandoff() {
        return "🤖 Te estoy transfiriendo con uno de nuestros asesores humanos. "
                + "En breve te responderemos por este mismo medio.\n\n"
                + "💡 Nota: Si deseas cancelar la espera y volver al menú principal "
                + "en cualquier momento, simplemente escribe la palabra INICIO.";
    }

    /**
     * Alerta para el asesor/admin cuando un usuario pide atención humana.
     *
     * @param profileName nombre público del usuario (o su Wa ID si no hay nombre)
     * @param waId        identificador de WhatsApp del usuario
     */
    public static String adminAlert(String profileName, String waId) {
        String display = (profileName == null || profileName.isBlank()) ? waId : profileName;
        return "🚨 Alerta de Asesor: El usuario " + display + " (Wa ID: " + waId + ") "
                + "ha solicitado asistencia humana. Por favor, revisa la bandeja de entrada de Meta Business.";
    }

    // ── Helpers internos ─────────────────────────────────────────────────

    /** {@code stateKey} de la selección de Nivel 3 según la rama. */
    private static String detailStateKey(ServiceBranch branch) {
        return branch == ServiceBranch.PURCHASE ? "PRODUCT_MENU" : branch.stateKey();
    }

    /** Identificación del usuario: {@code Juan Pérez (CC ****0765)}. */
    private static String userDescriptor(List<ConversationSelection> selections) {
        String name = labelAt(selections, 4, "NAME_INPUT").orElse("No registrado");
        ConversationSelection documentType = selectionAt(selections, 4, "IDENTIFICATION_MENU")
                .orElse(null);
        String maskedDocument = labelAt(selections, 4, "DOCUMENT_INPUT").orElse("N/A");

        if (documentType == null) {
            return name;
        }
        if (DocumentValidator.DocumentType.NEW_CLIENT.name().equals(documentType.optionKey())) {
            return name + " (cliente nuevo)";
        }
        return name + " (" + DocumentValidator.shortLabel(documentType.optionKey())
                + " " + maskedDocument + ")";
    }

    /** Emoji de la línea de detalle (Nivel 3). */
    private static String detailEmoji(ServiceBranch branch) {
        return branch == ServiceBranch.PURCHASE ? "📦" : "📝";
    }

    private static Optional<String> labelAt(List<ConversationSelection> selections, int level,
                                            String stateKey) {
        return selectionAt(selections, level, stateKey).map(ConversationSelection::displayLabel);
    }

    private static Optional<ConversationSelection> selectionAt(List<ConversationSelection> selections,
                                                               int level, String stateKey) {
        if (selections == null) {
            return Optional.empty();
        }
        return selections.stream()
                .filter(s -> s.level() == level && stateKey.equals(s.stateKey()))
                .findFirst();
    }
}
