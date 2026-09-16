package com.botwap.domain.menu;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalogo centralizado de menus y sus opciones (Niveles 1-3 y 5).
 *
 * <p>Cada menu esta identificado por stateKey (coincide con el ConversationState)
 * y level. Las claves de opcion son semanticas y estables para persistencia
 * en conversation_selection.option_key; el emoji es solo presentacion.</p>
 */
public final class MenuCatalog {

    private MenuCatalog() {
    }

    public static final String MAIN_MENU_STATE = "MAIN_MENU";

    public static List<MenuOption> mainMenu() {
        return List.of(
                new MenuOption("PURCHASE", "🛒", "Compra de paquetes"),
                new MenuOption("RECHARGE", "📲", "Recargas"),
                new MenuOption("COMPLAINT", "📋", "Quejas o reclamos"),
                new MenuOption("PERSONAL_INFO", "🔎", "Información personal"),
                new MenuOption("SUPPORT", "🛠️", "Soporte técnico")
        );
    }

    public static List<MenuOption> purchaseMenu() {
        return List.of(
                new MenuOption("INTERNET_MOBILE", "📶", "Internet móvil"),
                new MenuOption("UNLIMITED_MINUTES", "⏱️", "Minutos ilimitados"),
                new MenuOption("SOCIAL_MEDIA", "💬", "Redes sociales"),
                new MenuOption("ALL_INCLUSIVE", "✨", "Todo incluido")
        );
    }

    public static List<MenuOption> rechargeMenu() {
        return List.of(
                new MenuOption("RECHARGE_MY_LINE", "📱", "Recargar mi línea"),
                new MenuOption("RECHARGE_OTHER_NUMBER", "🔢", "Recargar otro número"),
                new MenuOption("USE_POINTS", "🎁", "Usar puntos"),
                new MenuOption("INTERNATIONAL_RECHARGE", "🌎", "Recarga internacional")
        );
    }

    public static List<MenuOption> complaintMenu() {
        return List.of(
                new MenuOption("BILLING", "🧾", "Facturación"),
                new MenuOption("COVERAGE", "📡", "Cobertura"),
                new MenuOption("SPEED_QUALITY", "🚀", "Velocidad y calidad"),
                new MenuOption("EQUIPMENT_PORTABILITY", "📦", "Equipos y portabilidad")
        );
    }

    public static List<MenuOption> personalInfoMenu() {
        return List.of(
                new MenuOption("CHECK_BALANCE", "💰", "Consultar saldo"),
                new MenuOption("CURRENT_PLAN", "📋", "Consultar plan actual"),
                new MenuOption("CHANGE_PLAN", "🔄", "Cambiar plan"),
                new MenuOption("UPDATE_DATA", "✏️", "Actualizar datos")
        );
    }

    public static List<MenuOption> supportMenu() {
        return List.of(
                new MenuOption("APN_CONFIG", "⚙️", "Configuración APN"),
                new MenuOption("CALLS_MESSAGES", "📞", "Llamadas y mensajes"),
                new MenuOption("SIGNAL_ISSUES", "📶", "Problemas de señal"),
                new MenuOption("CONFIG_GUIDES", "📚", "Guías de configuración")
        );
    }

    /** Submenú de nivel 3 para Recargas (montos). */
    public static List<MenuOption> rechargeDetailMenu() {
        return List.of(
                new MenuOption("AMOUNT_5000", "💵", "5.000 COP"),
                new MenuOption("AMOUNT_10000", "💵", "10.000 COP"),
                new MenuOption("AMOUNT_20000", "💵", "20.000 COP"),
                new MenuOption("AMOUNT_50000", "💵", "50.000 COP")
        );
    }

    /** Submenú de nivel 3 para Quejas o reclamos (motivo del caso). */
    public static List<MenuOption> complaintDetailMenu() {
        return List.of(
                new MenuOption("BILL_REVIEW", "🧾", "Revisión de factura"),
                new MenuOption("COMPENSATION", "🎁", "Solicitar compensación"),
                new MenuOption("SERVICE_FAILURE", "🚨", "Reporte de falla"),
                new MenuOption("CLAIM_FOLLOWUP", "🔍", "Seguimiento de reclamo")
        );
    }

    /** Submenú de nivel 3 para Información personal (detalle). */
    public static List<MenuOption> personalInfoDetailMenu() {
        return List.of(
                new MenuOption("LAST_MOVEMENTS", "📊", "Últimos movimientos"),
                new MenuOption("AVAILABLE_BALANCE", "💰", "Saldo disponible"),
                new MenuOption("BILLING_DETAILS", "🧾", "Datos de facturación"),
                new MenuOption("LINE_STATUS", "📡", "Estado de línea")
        );
    }

    /** Submenú de nivel 3 para Soporte técnico (tipo de guía). */
    public static List<MenuOption> supportDetailMenu() {
        return List.of(
                new MenuOption("STEP_BY_STEP", "📝", "Guía paso a paso"),
                new MenuOption("VIDEO_TUTORIAL", "🎬", "Video tutorial"),
                new MenuOption("MANUAL_CONFIG", "⚙️", "Configuración manual"),
                new MenuOption("SELF_DIAGNOSTIC", "🩺", "Autodiagnóstico")
        );
    }

    /** Submenú de nivel 3 coherente para el detalle de la categoría indicada. */
    public static List<MenuOption> detailOptionsFor(String categoryStateKey) {
        return switch (categoryStateKey) {
            case "RECHARGE_MENU" -> rechargeDetailMenu();
            case "COMPLAINT_MENU" -> complaintDetailMenu();
            case "PERSONAL_INFO_MENU" -> personalInfoDetailMenu();
            case "SUPPORT_MENU" -> supportDetailMenu();
            default -> productMenu();
        };
    }

    public static List<MenuOption> productMenu() {
        return List.of(
                new MenuOption("5GB", "📶", "5 GB"),
                new MenuOption("10GB", "📶", "10 GB"),
                new MenuOption("20GB", "📶", "20 GB"),
                new MenuOption("UNLIMITED", "♾️", "Ilimitado")
        );
    }

    public static List<MenuOption> identificationMenu() {
        return List.of(
                new MenuOption("CC", "🪪", "Cédula de ciudadanía"),
                new MenuOption("PASSPORT", "🛂", "Pasaporte"),
                new MenuOption("NIT", "🏢", "NIT"),
                new MenuOption("NEW_CLIENT", "🆕", "Soy cliente nuevo")
        );
    }

    /**
     * Acciones de la confirmación (Nivel 5).
     *
     * @param purchaseBranch {@code true} en la rama de compra, donde la acción de
     *                       volver atrás se refiere explícitamente al paquete.
     */
    public static List<MenuOption> confirmationMenu(boolean purchaseBranch) {
        return List.of(
                new MenuOption("CONFIRM", "✅", "Confirmar"),
                new MenuOption("CHANGE_PACKAGE", "🔄",
                        purchaseBranch ? "Cambiar paquete" : "Cambiar opción"),
                new MenuOption("AGENT", "💬", "Hablar con un asesor"),
                new MenuOption("CANCEL", "❌", "Cancelar")
        );
    }

    /** Acciones de la confirmación con la etiqueta genérica de «cambiar». */
    public static List<MenuOption> confirmationMenu() {
        return confirmationMenu(false);
    }

    private static final Map<String, List<MenuOption>> MENUS_BY_STATE = buildMenusByState();

    private static Map<String, List<MenuOption>> buildMenusByState() {
        Map<String, List<MenuOption>> m = new LinkedHashMap<>();
        m.put("MAIN_MENU", mainMenu());
        m.put("PURCHASE_MENU", purchaseMenu());
        m.put("RECHARGE_MENU", rechargeMenu());
        m.put("COMPLAINT_MENU", complaintMenu());
        m.put("PERSONAL_INFO_MENU", personalInfoMenu());
        m.put("SUPPORT_MENU", supportMenu());
        m.put("PRODUCT_MENU", productMenu());
        m.put("IDENTIFICATION_MENU", identificationMenu());
        m.put("CONFIRMATION_MENU", confirmationMenu());
        return Map.copyOf(m);
    }

    public static List<MenuOption> optionsFor(String stateKey) {
        return MENUS_BY_STATE.getOrDefault(stateKey, List.of());
    }
}
