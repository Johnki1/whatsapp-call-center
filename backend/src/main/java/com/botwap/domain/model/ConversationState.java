package com.botwap.domain.model;

/**
 * Estados de la conversación definidos en la arquitectura aprobada
 * (docs/CONVERSATION_FLOW.md § 4).
 *
 * <p>El atributo {@code level} documenta el nivel jerárquico del estado
 * (N1 menú principal … N5 confirmación); los estados terminales no tienen nivel.</p>
 */
public enum ConversationState {

    /** Nivel 1 — Menú principal. */
    MAIN_MENU(1),

    /** Nivel 2 — Servicio/categoría: Compra de paquetes. */
    PURCHASE_MENU(2),

    /** Nivel 2 — Servicio/categoría: Recargas. */
    RECHARGE_MENU(2),

    /** Nivel 2 — Servicio/categoría: Quejas o reclamos. */
    COMPLAINT_MENU(2),

    /** Nivel 2 — Servicio/categoría: Información personal. */
    PERSONAL_INFO_MENU(2),

    /** Nivel 2 — Servicio/categoría: Soporte técnico. */
    SUPPORT_MENU(2),

    /** Nivel 3 — Producto/subservicio (con contexto del producto elegido en Fase 4). */
    PRODUCT_MENU(3),

    /** Nivel 3 — Submenú de detalle del caso (quejas/soporte/información). */
    DETAIL_MENU(3),

    /** Nivel 4 — Identificación: ingreso del nombre completo del usuario. */
    NAME_INPUT(4),

    /** Nivel 4 — Identificación: selección del tipo de documento. */
    IDENTIFICATION_MENU(4),

    /** Nivel 4 — Identificación: ingreso del número de documento (texto libre). */
    DOCUMENT_INPUT(4),

    /** Nivel 5 — Confirmación de la solicitud. */
    CONFIRMATION_MENU(5),

    /** Estado terminal — Respuesta final emitida. */
    FINAL(-1),

    /** Estado terminal — Conversación cancelada. */
    CANCELLED(-1),

    /**
     * Estado terminal — Handoff a un asesor humano.
     *
     * <p>El bot anuncia la transferencia y luego permanece en silencio
     * ({@code EngineResult.silent()} permanente): la conversación la retoma una
     * persona por el mismo canal de WhatsApp. Un reinicio explícito
     * ({@code hola}/{@code menu}) abre una conversación nueva.</p>
     */
    HUMAN_AGENT(-1);

    private final int level;

    ConversationState(int level) {
        this.level = level;
    }

    /** Nivel jerárquico del estado (de la definición oficial de los 5 niveles). */
    public int level() {
        return level;
    }

    /**
     * Indica si el estado es terminal: la conversación queda cerrada y el bot
     * debe permanecer en silencio hasta que el usuario reinicie explícitamente.
     */
    public boolean isTerminal() {
        return level < 0;
    }
}