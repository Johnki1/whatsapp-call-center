package com.botwap.domain.engine;

import com.botwap.domain.model.ConversationSelection;
import com.botwap.domain.model.ConversationState;

import java.util.List;
import java.util.Optional;

/**
 * Rama de servicio elegida por el usuario en el Nivel 1 (menú principal).
 *
 * <p>Es la fuente única de verdad para el <em>contexto</em> del flujo: nombre
 * visible del servicio, etiqueta de la selección de Nivel 3 (un reclamo NO se
 * describe como "Paquete"), estado al que se regresa al "cambiar opción" y la
 * despedida de éxito correspondiente.</p>
 *
 * <p>El {@code optionKey} coincide con el de {@link com.botwap.domain.menu.MenuCatalog#mainMenu()}
 * y el {@code stateKey} del Nivel 2 se deriva como {@code <nombre>_MENU}.</p>
 */
public enum ServiceBranch {

    PURCHASE("🛒", "Compra de paquetes", "Paquete", ConversationState.PRODUCT_MENU,
            "✅ Tu compra se procesó exitosamente.",
            "Activaremos tu paquete en los próximos minutos. 📲"),

    RECHARGE("📲", "Recargas", "Monto", ConversationState.DETAIL_MENU,
            "✅ Tu recarga se procesó exitosamente.",
            "Verás el saldo reflejado en tu línea en pocos minutos. 📱"),

    COMPLAINT("📋", "Quejas o reclamos", "Motivo", ConversationState.DETAIL_MENU,
            "✅ Tu reclamo ha sido radicado con éxito.",
            "Un asesor revisará tu caso y te contactará por este medio. 🕓"),

    PERSONAL_INFO("🔎", "Información personal", "Consulta", ConversationState.DETAIL_MENU,
            "✅ Tu consulta se procesó exitosamente.",
            "Te enviamos la información solicitada a esta conversación. 📄"),

    SUPPORT("🛠️", "Soporte técnico", "Tema", ConversationState.DETAIL_MENU,
            "✅ Tu solicitud de soporte fue registrada con éxito.",
            "Un especialista técnico continuará la atención de tu caso. 🧑‍🔧"),

    /** Fallback defensivo cuando la conversación no tiene selección de Nivel 1. */
    UNKNOWN("📌", "Servicio al cliente", "Detalle", ConversationState.MAIN_MENU,
            "✅ Tu solicitud se procesó exitosamente.",
            "Escribe *hola* cuando quieras iniciar una nueva solicitud. 👋");

    private final String emoji;
    private final String displayName;
    private final String detailLabel;
    private final ConversationState backState;
    private final String successMessage;
    private final String successHint;

    ServiceBranch(String emoji, String displayName, String detailLabel,
                  ConversationState backState, String successMessage, String successHint) {
        this.emoji = emoji;
        this.displayName = displayName;
        this.detailLabel = detailLabel;
        this.backState = backState;
        this.successMessage = successMessage;
        this.successHint = successHint;
    }

    /** Emoji representativo de la rama. */
    public String emoji() {
        return emoji;
    }

    /** Nombre visible del servicio (ej. «Quejas o reclamos»). */
    public String displayName() {
        return displayName;
    }

    /** Etiqueta de la selección de Nivel 3 según la rama (ej. «Motivo», «Paquete»). */
    public String detailLabel() {
        return detailLabel;
    }

    /** Estado al que se regresa al elegir «cambiar opción» en la confirmación. */
    public ConversationState backState() {
        return backState;
    }

    /** Despedida contextual de éxito para esta rama. */
    public String successMessage() {
        return successMessage;
    }

    /** Texto de seguimiento mostrado junto a la despedida. */
    public String successHint() {
        return successHint;
    }

    /** {@code stateKey} del Nivel 2 asociado a la rama (ej. {@code COMPLAINT_MENU}). */
    public String stateKey() {
        return this == UNKNOWN ? "MAIN_MENU" : name() + "_MENU";
    }

    /** Rama a partir del {@code optionKey} del menú principal. */
    public static ServiceBranch fromOptionKey(String optionKey) {
        if (optionKey == null) {
            return UNKNOWN;
        }
        for (ServiceBranch branch : values()) {
            if (branch.name().equals(optionKey)) {
                return branch;
            }
        }
        return UNKNOWN;
    }

    /**
     * Rama activa deducida de la selección de Nivel 1 persistida.
     *
     * <p>Resuelve por {@code stateKey = MAIN_MENU} (no por "nivel"), de modo que
     * las selecciones de ramas anteriores que sigan almacenadas en otros niveles
     * no contaminan el contexto actual.</p>
     */
    public static ServiceBranch fromSelections(List<ConversationSelection> selections) {
        return optionKeyFor(selections, 1, "MAIN_MENU")
                .map(ServiceBranch::fromOptionKey)
                .orElse(UNKNOWN);
    }

    /**
     * {@code optionKey} de la selección del (nivel, stateKey) indicados.
     *
     * <p>Es la clave para resolver semántica (rama, tipo de documento); la
     * etiqueta visible se obtiene con {@link #labelFor(List, int, String)}.</p>
     */
    public static Optional<String> optionKeyFor(List<ConversationSelection> selections, int level,
                                                String stateKey) {
        return selectionAt(selections, level, stateKey).map(ConversationSelection::optionKey);
    }

    /** Etiqueta visible de la selección del (nivel, stateKey) indicados. */
    public static Optional<String> labelFor(List<ConversationSelection> selections, int level,
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
