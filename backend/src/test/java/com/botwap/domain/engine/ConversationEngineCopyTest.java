package com.botwap.domain.engine;

import com.botwap.domain.model.Conversation;
import com.botwap.domain.model.ConversationSelection;
import com.botwap.domain.model.ConversationState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias (sin Spring ni base de datos) del motor conversacional y del
 * copywriting: orden Nombre → Tipo de identificación → Número de documento,
 * resumen de confirmación contextual/personalizado, despedidas por rama y
 * silencio en estados terminales.
 */
class ConversationEngineCopyTest {

    private static final UUID CONV_ID = UUID.randomUUID();
    private static final String WA_ID = "573001234567";

    private final ConversationEngine engine = new ConversationEngineImpl();

    // ----------------------------------------------------------------
    // Flujo Nombre → Tipo → Número
    // ----------------------------------------------------------------

    @Test
    void capturaNombreTipoYNumeroAntesDeConfirmar() {
        List<ConversationSelection> selections = new ArrayList<>();

        // 1. Bienvenida
        EngineResult welcome = process(ConversationState.MAIN_MENU, "hola", selections);
        assertThat(welcome.nextState()).isEqualTo(ConversationState.MAIN_MENU);
        assertThat(welcome.responseText()).contains("👋");
        assertThat(welcome.hasMenu()).isTrue();

        // 2-4. Compra de paquetes -> Internet móvil -> 10 GB
        step(ConversationState.MAIN_MENU, "1", selections, ConversationState.PURCHASE_MENU);
        step(ConversationState.PURCHASE_MENU, "1", selections, ConversationState.PRODUCT_MENU);

        EngineResult askName = process(ConversationState.PRODUCT_MENU, "2", selections);
        assertThat(askName.nextState()).isEqualTo(ConversationState.NAME_INPUT);
        assertThat(askName.responseText()).contains("Cuéntame tu nombre completo");
        record(askName, selections);

        // 5. El nombre SIEMPRE precede al tipo de documento.
        EngineResult askDocumentType = process(ConversationState.NAME_INPUT, "Juan Pérez", selections);
        assertThat(askDocumentType.nextState()).isEqualTo(ConversationState.IDENTIFICATION_MENU);
        assertThat(askDocumentType.responseText()).contains("Juan Pérez");
        assertThat(askDocumentType.responseText()).contains("tipo de identificación");
        assertThat(askDocumentType.hasMenu()).isTrue();
        record(askDocumentType, selections);

        // 6-7. Tipo y número de documento -> resumen de confirmación.
        EngineResult askDocument = process(ConversationState.IDENTIFICATION_MENU, "1", selections);
        assertThat(askDocument.nextState()).isEqualTo(ConversationState.DOCUMENT_INPUT);
        record(askDocument, selections);

        EngineResult summary = process(ConversationState.DOCUMENT_INPUT, "1234567890", selections);
        assertThat(summary.nextState()).isEqualTo(ConversationState.CONFIRMATION_MENU);
        assertThat(summary.responseText())
                .contains("👤 *Usuario:* Juan Pérez (CC ******7890)")
                .contains("*Paquete:* 10 GB")
                .contains("¿Confirmas que los datos son correctos?");
        record(summary, selections);

        // 8. Despedida contextual de compra.
        EngineResult farewell = process(ConversationState.CONFIRMATION_MENU, "1", selections);
        assertThat(farewell.nextState()).isEqualTo(ConversationState.FINAL);
        assertThat(farewell.responseText()).contains("✅ Tu compra se procesó exitosamente.");
    }

    @Test
    void nombreInvalidoNoAvanzaNiCapturaTipoDeDocumento() {
        List<ConversationSelection> selections = new ArrayList<>();

        EngineResult invalid = process(ConversationState.NAME_INPUT, "12345", selections);
        assertThat(invalid.nextState()).isEqualTo(ConversationState.NAME_INPUT);
        assertThat(invalid.selection()).isNull();
        assertThat(invalid.responseText()).contains("letras y espacios");

        // Sin tipo de documento registrado no se puede saltar a DOCUMENT_INPUT.
        EngineResult skipped = process(ConversationState.DOCUMENT_INPUT, "1234567890", selections);
        assertThat(skipped.nextState()).isEqualTo(ConversationState.IDENTIFICATION_MENU);
        assertThat(skipped.nextState()).isEqualTo(ConversationState.IDENTIFICATION_MENU);
        assertThat(skipped.hasMenu()).isTrue();
    }

    // ----------------------------------------------------------------
    // Terminal: el bot permanece en silencio
    // ----------------------------------------------------------------

    @Test
    void estadoFinalNoEmiteRespuestaSinReinicioExplicito() {
        EngineResult silent = process(ConversationState.FINAL, "gracias", List.of());
        assertThat(silent.hasResponse()).isFalse();
        assertThat(silent.nextState()).isEqualTo(ConversationState.FINAL);

        EngineResult alsoSilent = process(ConversationState.CANCELLED, "1", List.of());
        assertThat(alsoSilent.hasResponse()).isFalse();
    }

    @Test
    void estadoFinalRespondeSoloAReinicioExplicito() {
        EngineResult restarted = process(ConversationState.FINAL, "hola", List.of());
        assertThat(restarted.hasResponse()).isTrue();
        assertThat(restarted.nextState()).isEqualTo(ConversationState.MAIN_MENU);
    }

    // ----------------------------------------------------------------
    // Copy contextual por rama
    // ----------------------------------------------------------------

    @Test
    void resumenDeReclamoUsaMotivoYNoPaquete() {
        String summary = BotCopy.confirmationPrompt(complaintSelections());

        assertThat(summary)
                .contains("🧾 *Resumen de tu solicitud*")
                .contains("👤 *Usuario:* Juan Pérez (CC ****0765)")
                .contains("📋 *Servicio:* Quejas o reclamos")
                .contains("📝 *Motivo:* Solicitar compensación")
                .doesNotContain("*Paquete:*");
    }

    @Test
    void despedidasSonContextualesALaRama() {
        assertThat(BotCopy.success(complaintSelections()))
                .contains("✅ Tu reclamo ha sido radicado con éxito.");

        assertThat(BotCopy.success(purchaseSelections()))
                .contains("✅ Tu compra se procesó exitosamente.")
                .contains("📦 *Paquete:* 10 GB");
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private EngineResult process(ConversationState state, String input,
                                 List<ConversationSelection> selections) {
        EngineRequest request = new EngineRequest(CONV_ID, WA_ID, input, state, null, List.copyOf(selections));
        return engine.process(request, Conversation.newActive(WA_ID, null));
    }

    private void step(ConversationState state, String input, List<ConversationSelection> selections,
                      ConversationState expected) {
        EngineResult result = process(state, input, selections);
        assertThat(result.nextState()).isEqualTo(expected);
        record(result, selections);
    }

    /** Simula la persistencia de la selección devuelta por el motor. */
    private static void record(EngineResult result, List<ConversationSelection> selections) {
        if (result.selection() != null) {
            selections.add(result.selection());
        }
    }

    private static List<ConversationSelection> complaintSelections() {
        return List.of(
                ConversationSelection.unpersisted(1, "MAIN_MENU", "COMPLAINT", "Quejas o reclamos", "{}"),
                ConversationSelection.unpersisted(2, "COMPLAINT_MENU", "BILLING", "Facturación", "{}"),
                ConversationSelection.unpersisted(3, "COMPLAINT_MENU", "COMPENSATION",
                        "Solicitar compensación", "{}"),
                ConversationSelection.unpersisted(4, "NAME_INPUT", "NAME_SUBMITTED", "Juan Pérez", "{}"),
                ConversationSelection.unpersisted(4, "IDENTIFICATION_MENU", "CC",
                        "Cédula de ciudadanía", "{}"),
                ConversationSelection.unpersisted(4, "DOCUMENT_INPUT", "DOCUMENT_SUBMITTED",
                        "****0765", "{}"));
    }

    private static List<ConversationSelection> purchaseSelections() {
        return List.of(
                ConversationSelection.unpersisted(1, "MAIN_MENU", "PURCHASE", "Compra de paquetes", "{}"),
                ConversationSelection.unpersisted(2, "PURCHASE_MENU", "INTERNET_MOBILE",
                        "Internet móvil", "{}"),
                ConversationSelection.unpersisted(3, "PRODUCT_MENU", "10GB", "10 GB", "{}"),
                ConversationSelection.unpersisted(4, "NAME_INPUT", "NAME_SUBMITTED", "Juan Pérez", "{}"),
                ConversationSelection.unpersisted(4, "IDENTIFICATION_MENU", "CC",
                        "Cédula de ciudadanía", "{}"),
                ConversationSelection.unpersisted(4, "DOCUMENT_INPUT", "DOCUMENT_SUBMITTED",
                        "****0765", "{}"));
    }

    // ----------------------------------------------------------------
    // Fase V2 actualizacion Fase 4: HUMAN_AGENT (doble mensaje + despertador)
    // ----------------------------------------------------------------

    @Test
    void handoffGeneraDobleMensajeConNotaDeInicio() {
        String client = BotCopy.humanHandoff();
        assertThat(client).contains("transfiriendo").contains("INICIO");
        String admin = BotCopy.adminAlert("Jhonki", "573001234567");
        assertThat(admin).contains("573234198831".substring(0, 3));
        assertThat(admin).contains("Jhonki").contains("573001234567");
        assertThat(com.botwap.application.service.InboundMessageOrchestrator.ADMIN_PHONE)
                .isEqualTo("573234198831");
    }

    @Test
    void despertadorReconoceInicioYMenu() {
        assertThat(com.botwap.application.service.InboundMessageOrchestrator.isWakeWord("INICIO")).isTrue();
        assertThat(com.botwap.application.service.InboundMessageOrchestrator.isWakeWord("  menu ")).isTrue();
        assertThat(com.botwap.application.service.InboundMessageOrchestrator.isWakeWord("hola")).isFalse();
    }

    @Test
    void humanAgentPermaneceEnSilencioSinDespertador() {
        EngineResult silent = process(ConversationState.HUMAN_AGENT, "gracias", List.of());
        assertThat(silent.hasResponse()).isFalse();
        assertThat(silent.nextState()).isEqualTo(ConversationState.HUMAN_AGENT);
    }
}
