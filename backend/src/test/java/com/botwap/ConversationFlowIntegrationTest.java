package com.botwap;

import com.botwap.domain.model.ConversationState;
import com.botwap.domain.model.OutboxMessage;
import com.botwap.domain.port.ConversationRepository;
import com.botwap.domain.port.MessageRepository;
import com.botwap.domain.port.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de integración del flujo conversacional completo (Fase 5).
 *
 * <p>Usa WebTestClient + Testcontainers PostgreSQL real. Verifica los 5 niveles,
 * aislamiento entre usuarios, idempotencia por wamid, concurrencia del mismo
 * usuario respaldada por PostgreSQL (FOR UPDATE + optimistic lock), rollback
 * transaccional y garantías del Outbox.</p>
 */
class ConversationFlowIntegrationTest extends BaseIntegrationTest {

    private static final String SECRET = "test-app-secret";

    @Autowired
    DatabaseClient databaseClient;

    @Autowired
    WebTestClient client;

    @BeforeEach
    void cleanDatabase() {
        databaseClient.sql("""
                        TRUNCATE TABLE outbox_message, message, conversation_selection, conversation
                        RESTART IDENTITY CASCADE
                        """)
                .then()
                .block();
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private WebTestClient.ResponseSpec send(String waId, String wamid, String text) {
        String payload = payload(waId, wamid, text);
        return client.post()
                .uri("/webhook/whatsapp")
                .header("X-Hub-Signature-256", sign(SECRET, payload))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchange()
                .expectStatus().isOk();
    }

    private static String payload(String waId, String wamid, String text) {
        return "{\"object\":\"waba\",\"entry\":[{\"id\":\"WABA\",\"changes\":[{" +
                "\"field\":\"messages\",\"value\":{\"messages\":[{" +
                "\"from\":\"" + waId + "\",\"id\":\"" + wamid + "\",\"type\":\"text\"," +
                "\"text\":{\"body\":\"" + text + "\"}}]}}]}]}";
    }

    private static String sign(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private Mono<Map<String, Object>> conversationRow(String waId) {
        return databaseClient.sql("""
                        SELECT state, status, version FROM conversation WHERE wa_id = :waId
                        """)
                .bind("waId", waId)
                .fetch()
                .first()
                .map(row -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("state", row.get("state"));
                    m.put("status", row.get("status"));
                    m.put("version", ((Number) row.get("version")).longValue());
                    return m;
                })
                .defaultIfEmpty(Map.of());
    }

    private Mono<Long> count(String sql) {
        return databaseClient.sql(sql)
                .fetch()
                .first()
                .map(row -> ((Number) row.get("cnt")).longValue());
    }

    private Mono<Long> countOutboxPending() {
        return count("SELECT COUNT(*) AS cnt FROM outbox_message WHERE status = 'PENDING'");
    }

    private Mono<Long> countOutboxSent() {
        return count("SELECT COUNT(*) AS cnt FROM outbox_message WHERE status = 'SENT'");
    }

    private Mono<Long> countInbound() {
        return count("SELECT COUNT(*) AS cnt FROM message WHERE direction = 'INBOUND'");
    }

    private Mono<Long> countOutbox() {
        return count("SELECT COUNT(*) AS cnt FROM outbox_message");
    }

    private Mono<Integer> topLevel(String waId) {
        return databaseClient.sql("""
                        SELECT COALESCE(MAX(cs.level), 0) AS lv
                        FROM conversation_selection cs
                        JOIN conversation c ON c.id = cs.conversation_id
                        WHERE c.wa_id = :waId
                        """)
                .bind("waId", waId)
                .fetch()
                .first()
                .map(row -> ((Number) row.get("lv")).intValue())
                .defaultIfEmpty(0);
    }

    private Mono<Boolean> hasSelection(String waId, String optionKey) {
        return databaseClient.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM conversation_selection cs
                            JOIN conversation c ON c.id = cs.conversation_id
                            WHERE c.wa_id = :waId AND cs.option_key = :optionKey
                        ) AS ex
                        """)
                .bind("waId", waId)
                .bind("optionKey", optionKey)
                .fetch()
                .first()
                .map(row -> (Boolean) row.get("ex"));
    }

    private void postNoAssert(String waId, String wamid, String text) {
        String payload = payload(waId, wamid, text);
        client.post()
                .uri("/webhook/whatsapp")
                .header("X-Hub-Signature-256", sign(SECRET, payload))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void flujoCompraCompletoLlegaAFinalConTrazabilidad() {
        send("573001111111", "w1", "hola");
        send("573001111111", "w2", "1");          // Compra -> PURCHASE_MENU (N2)
        send("573001111111", "w3", "1");          // Internet movil -> PRODUCT_MENU (N3)
        send("573001111111", "w4", "2");          // 10 GB -> NAME_INPUT (N4)
        send("573001111111", "w5", "Juan Perez"); // Nombre -> IDENTIFICATION_MENU (N4)
        send("573001111111", "w6", "1");          // CC -> DOCUMENT_INPUT (N4)
        send("573001111111", "w7", "1234567890"); // documento -> CONFIRMATION_MENU (N5)
        send("573001111111", "w8", "1");          // Confirmar -> FINAL

        StepVerifier.create(conversationRow("573001111111"))
                .assertNext(row -> {
                    assertThat(row.get("state")).isEqualTo("FINAL");
                    assertThat(row.get("status")).isEqualTo("CLOSED");
                    assertThat((Long) row.get("version")).isPositive();
                })
                .verifyComplete();

        StepVerifier.create(hasSelection("573001111111", "PURCHASE")).assertNext(b -> assertThat(b).isTrue()).verifyComplete();
        StepVerifier.create(hasSelection("573001111111", "INTERNET_MOBILE")).assertNext(b -> assertThat(b).isTrue()).verifyComplete();
        StepVerifier.create(hasSelection("573001111111", "10GB")).assertNext(b -> assertThat(b).isTrue()).verifyComplete();
        StepVerifier.create(hasSelection("573001111111", "NAME_SUBMITTED")).assertNext(b -> assertThat(b).isTrue()).verifyComplete();
        StepVerifier.create(hasSelection("573001111111", "CC")).assertNext(b -> assertThat(b).isTrue()).verifyComplete();
        StepVerifier.create(hasSelection("573001111111", "DOCUMENT_SUBMITTED")).assertNext(b -> assertThat(b).isTrue()).verifyComplete();

        // 8 respuestas encoladas PENDING: nada se envía dentro de la transacción.
        StepVerifier.create(countOutboxPending()).assertNext(n -> assertThat(n).isEqualTo(8L)).verifyComplete();
        StepVerifier.create(countOutboxSent()).assertNext(n -> assertThat(n).isZero()).verifyComplete();
    }

    @Test
    void flujoRecargaCompleto() {
        send("W-A", "r1", "hola");
        send("W-A", "r2", "2");         // Recargas (N2)
        send("W-A", "r3", "1");         // Recargar mi linea -> DETAIL_MENU (N3)
        send("W-A", "r4", "2");         // 10.000 COP -> NAME_INPUT (N4)
        send("W-A", "r5", "Ana Gomez"); // Nombre -> IDENTIFICATION_MENU (N4)
        send("W-A", "r6", "3");         // NIT -> DOCUMENT_INPUT (N4)
        send("W-A", "r7", "900123456"); // NIT valido -> CONFIRMATION (N5)
        send("W-A", "r8", "4");         // Cancelar -> CANCELLED

        StepVerifier.create(conversationRow("W-A"))
                .assertNext(row -> {
                    assertThat(row.get("state")).isEqualTo("CANCELLED");
                    assertThat(row.get("status")).isEqualTo("CLOSED");
                })
                .verifyComplete();
        StepVerifier.create(hasSelection("W-A", "RECHARGE_MY_LINE")).assertNext(b -> assertThat(b).isTrue()).verifyComplete();
        StepVerifier.create(hasSelection("W-A", "AMOUNT_10000")).assertNext(b -> assertThat(b).isTrue()).verifyComplete();
        StepVerifier.create(hasSelection("W-A", "NIT")).assertNext(b -> assertThat(b).isTrue()).verifyComplete();
    }

    @Test
    void flujoQuejaCompleto() {
        send("W-B", "q1", "hola");
        send("W-B", "q2", "3");            // Quejas (N2)
        send("W-B", "q3", "1");            // Facturacion -> DETAIL_MENU (N3)
        send("W-B", "q4", "2");            // Solicitar compensacion -> NAME_INPUT (N4)
        send("W-B", "q5", "Maria Ruiz");   // Nombre -> IDENTIFICATION_MENU (N4)
        send("W-B", "q6", "4");            // Soy cliente nuevo -> DOCUMENT_INPUT (N4)
        send("W-B", "q7", "continuar");    // (no exige documento) -> CONFIRMATION (N5)
        send("W-B", "q8", "3");            // Hablar con asesor -> HUMAN_AGENT (doble mensaje)

        StepVerifier.create(conversationRow("W-B"))
                .assertNext(row -> {
                    assertThat(row.get("state")).isEqualTo("HUMAN_AGENT");
                    assertThat(row.get("status")).isEqualTo("ACTIVE");
                }).verifyComplete();
        StepVerifier.create(hasSelection("W-B", "BILLING")).assertNext(b -> assertThat(b).isTrue()).verifyComplete();
        StepVerifier.create(hasSelection("W-B", "COMPENSATION")).assertNext(b -> assertThat(b).isTrue()).verifyComplete();
    }

    @Test
    void flujoInfoPersonalCompleto() {
        send("W-C", "p1", "hola");
        send("W-C", "p2", "4");          // Informacion personal (N2)
        send("W-C", "p3", "1");          // Consultar saldo -> DETAIL_MENU (N3)
        send("W-C", "p4", "1");          // Ultimos movimientos -> NAME_INPUT (N4)
        send("W-C", "p5", "Luis Torres");// Nombre -> IDENTIFICATION_MENU (N4)
        send("W-C", "p6", "2");          // Pasaporte -> DOCUMENT_INPUT (N4)
        send("W-C", "p7", "AB12345");    // pasaporte valido -> CONFIRMATION (N5)
        send("W-C", "p8", "1");          // Confirmar -> FINAL

        StepVerifier.create(conversationRow("W-C"))
                .assertNext(row -> assertThat(row.get("state")).isEqualTo("FINAL")).verifyComplete();
        StepVerifier.create(hasSelection("W-C", "PASSPORT")).assertNext(b -> assertThat(b).isTrue()).verifyComplete();
        StepVerifier.create(hasSelection("W-C", "LAST_MOVEMENTS")).assertNext(b -> assertThat(b).isTrue()).verifyComplete();
    }

    @Test
    void flujoSoporteCompleto() {
        send("W-D", "s1", "hola");
        send("W-D", "s2", "5");            // Soporte tecnico (N2)
        send("W-D", "s3", "1");            // Config APN -> DETAIL_MENU (N3)
        send("W-D", "s4", "4");            // Autodiagnostico -> NAME_INPUT (N4)
        send("W-D", "s5", "Carla Diaz");   // Nombre -> IDENTIFICATION_MENU (N4)
        send("W-D", "s6", "1");            // CC -> DOCUMENT_INPUT (N4)
        send("W-D", "s7", "1122334455");   // CC valido -> CONFIRMATION (N5)
        send("W-D", "s8", "1");            // Confirmar -> FINAL

        StepVerifier.create(conversationRow("W-D"))
                .assertNext(row -> assertThat(row.get("state")).isEqualTo("FINAL")).verifyComplete();
        StepVerifier.create(hasSelection("W-D", "APN_CONFIG")).assertNext(b -> assertThat(b).isTrue()).verifyComplete();
        StepVerifier.create(hasSelection("W-D", "SELF_DIAGNOSTIC")).assertNext(b -> assertThat(b).isTrue()).verifyComplete();
    }

    @Test
    void elNumero3DependeDelEstado() {
        // En MAIN_MENU, 3 = Quejas o reclamos.
        send("W-E", "n1", "hola");
        send("W-E", "n2", "3");
        StepVerifier.create(hasSelection("W-E", "COMPLAINT")).assertNext(b -> assertThat(b).isTrue()).verifyComplete();
        StepVerifier.create(conversationRow("W-E")).assertNext(r -> assertThat(r.get("state")).isEqualTo("COMPLAINT_MENU")).verifyComplete();

        // En PURCHASE_MENU, 3 = Redes sociales (nunca "Quejas").
        send("W-F", "m1", "hola");
        send("W-F", "m2", "1");
        send("W-F", "m3", "3");
        StepVerifier.create(hasSelection("W-F", "SOCIAL_MEDIA")).assertNext(b -> assertThat(b).isTrue()).verifyComplete();
        StepVerifier.create(hasSelection("W-F", "COMPLAINT")).assertNext(b -> assertThat(b).isFalse()).verifyComplete();
    }

    @Test
    void entradaInvalidaEnMenuNoCambiaEstadoNiCreaSeleccion() {
        send("W-G", "i1", "hola");
        send("W-G", "i2", "9");            // opcion invalida en MAIN_MENU
        StepVerifier.create(conversationRow("W-G"))
                .assertNext(r -> assertThat(r.get("state")).isEqualTo("MAIN_MENU")).verifyComplete();
        StepVerifier.create(topLevel("W-G")).assertNext(lv -> assertThat(lv).isZero()).verifyComplete();
    }

    @Test
    void documentoInvalidoReintentaSinPerderProgresoNiRollback() {
        send("W-H", "d1", "hola");
        send("W-H", "d2", "1");            // Compra
        send("W-H", "d3", "1");            // Internet movil
        send("W-H", "d4", "1");            // 5 GB -> NAME_INPUT
        send("W-H", "d5", "Pedro Lima");   // Nombre -> IDENTIFICATION_MENU
        send("W-H", "d6", "1");            // CC
        send("W-H", "d7", "12A");          // CC invalido (letras + corto)
        // Sigue en DOCUMENT_INPUT: no avanza, no pierde selecciones previas, no rollbacka.
        StepVerifier.create(conversationRow("W-H"))
                .assertNext(r -> assertThat(r.get("state")).isEqualTo("DOCUMENT_INPUT")).verifyComplete();
        StepVerifier.create(hasSelection("W-H", "5GB")).assertNext(b -> assertThat(b).isTrue()).verifyComplete();
        StepVerifier.create(countInbound()).assertNext(n -> assertThat(n).isEqualTo(7L)).verifyComplete();
    }

    @Test
    void nombreInvalidoReintentaSinAvanzarDeNivel() {
        send("W-H2", "n1", "hola");
        send("W-H2", "n2", "1");           // Compra
        send("W-H2", "n3", "1");           // Internet movil
        send("W-H2", "n4", "1");           // 5 GB -> NAME_INPUT
        send("W-H2", "n5", "12345");       // nombre invalido (numeros)
        StepVerifier.create(conversationRow("W-H2"))
                .assertNext(r -> assertThat(r.get("state")).isEqualTo("NAME_INPUT")).verifyComplete();
        // Ni el tipo de documento ni el numero pueden capturarse sin un nombre valido.
        send("W-H2", "n6", "1");
        StepVerifier.create(conversationRow("W-H2"))
                .assertNext(r -> assertThat(r.get("state")).isEqualTo("NAME_INPUT")).verifyComplete();
        send("W-H2", "n7", "Rosa Pena");
        StepVerifier.create(conversationRow("W-H2"))
                .assertNext(r -> assertThat(r.get("state")).isEqualTo("IDENTIFICATION_MENU")).verifyComplete();
        StepVerifier.create(hasSelection("W-H2", "NAME_SUBMITTED")).assertNext(b -> assertThat(b).isTrue()).verifyComplete();
    }

    @Test
    void documentoCCValidoAvanza() {
        send("W-I", "c1", "hola");
        send("W-I", "c2", "1");
        send("W-I", "c3", "1");
        send("W-I", "c4", "1");            // 5 GB -> NAME_INPUT
        send("W-I", "c5", "Sofia Mora");   // Nombre
        send("W-I", "c6", "1");            // CC
        send("W-I", "c7", "123456789");    // CC valido
        StepVerifier.create(conversationRow("W-I"))
                .assertNext(r -> assertThat(r.get("state")).isEqualTo("CONFIRMATION_MENU")).verifyComplete();
    }

    @Test
    void documentoPasaporteValidoEInvalido() {
        send("W-J", "pp1", "hola");
        send("W-J", "pp2", "1");
        send("W-J", "pp3", "1");
        send("W-J", "pp4", "1");
        send("W-J", "pp5", "Ella Cruz");     // Nombre
        send("W-J", "pp6", "2");             // Pasaporte
        send("W-J", "pp7", "12");            // invalido (corto)
        StepVerifier.create(conversationRow("W-J")).assertNext(r -> assertThat(r.get("state")).isEqualTo("DOCUMENT_INPUT")).verifyComplete();
        send("W-J", "pp8", "AB12CD34");      // valido
        StepVerifier.create(conversationRow("W-J")).assertNext(r -> assertThat(r.get("state")).isEqualTo("CONFIRMATION_MENU")).verifyComplete();
    }

    @Test
    void documentoNitValidoEInvalido() {
        send("W-K", "nt1", "hola");
        send("W-K", "nt2", "1");
        send("W-K", "nt3", "1");
        send("W-K", "nt4", "1");
        send("W-K", "nt5", "Hugo Nino");    // Nombre
        send("W-K", "nt6", "3");            // NIT
        send("W-K", "nt7", "12A");          // invalido
        StepVerifier.create(conversationRow("W-K")).assertNext(r -> assertThat(r.get("state")).isEqualTo("DOCUMENT_INPUT")).verifyComplete();
        send("W-K", "nt8", "9001122334");   // NIT valido
        StepVerifier.create(conversationRow("W-K")).assertNext(r -> assertThat(r.get("state")).isEqualTo("CONFIRMATION_MENU")).verifyComplete();
    }

    @Test
    void clienteNuevoNoExigeDocumento() {
        send("W-L", "nc1", "hola");
        send("W-L", "nc2", "1");
        send("W-L", "nc3", "1");
        send("W-L", "nc4", "1");             // 5 GB -> NAME_INPUT
        send("W-L", "nc5", "Nuevo Cliente"); // Nombre
        send("W-L", "nc6", "4");             // Soy cliente nuevo
        send("W-L", "nc7", "no_tengo_doc");  // no exige documento
        StepVerifier.create(conversationRow("W-L"))
                .assertNext(r -> assertThat(r.get("state")).isEqualTo("CONFIRMATION_MENU")).verifyComplete();
    }

    @Test
    void comandoMenuRegresaAlMenuPrincipal() {
        send("W-M", "g1", "hola");
        send("W-M", "g2", "1");            // Compra
        send("W-M", "g3", "1");            // Internet movil (PRODUCT_MENU)
        send("W-M", "g4", "menu");         // comando global: vuelve al inicio
        StepVerifier.create(conversationRow("W-M"))
                .assertNext(r -> assertThat(r.get("state")).isEqualTo("MAIN_MENU")).verifyComplete();
    }

    @Test
    void comandoVolverRegresaAlMenuAnterior() {
        send("W-N", "v1", "hola");
        send("W-N", "v2", "1");            // Compra -> PURCHASE_MENU
        send("W-N", "v3", "1");            // Internet movil -> PRODUCT_MENU
        send("W-N", "v4", "volver");       // desde PRODUCT_MENU -> PURCHASE_MENU
        StepVerifier.create(conversationRow("W-N"))
                .assertNext(r -> assertThat(r.get("state")).isEqualTo("PURCHASE_MENU")).verifyComplete();
    }

    @Test
    void comandoCancelarLlevaACancelled() {
        send("W-O", "c1", "hola");
        send("W-O", "c2", "1");
        send("W-O", "c3", "1");
        send("W-O", "c4", "cancelar");
        StepVerifier.create(conversationRow("W-O"))
                .assertNext(r -> {
                    assertThat(r.get("state")).isEqualTo("CANCELLED");
                    assertThat(r.get("status")).isEqualTo("CLOSED");
                }).verifyComplete();
    }

    @Test
    void cancelarEnDocumentInputNoCuentaComoDocumento() {
        send("W-P", "cd1", "hola");
        send("W-P", "cd2", "1");
        send("W-P", "cd3", "1");
        send("W-P", "cd4", "1");             // 5 GB -> NAME_INPUT
        send("W-P", "cd5", "Ivan Rojas");    // Nombre
        send("W-P", "cd6", "1");             // CC -> DOCUMENT_INPUT
        send("W-P", "cd7", "cancelar");      // comando global prevalece sobre el documento
        StepVerifier.create(conversationRow("W-P"))
                .assertNext(r -> assertThat(r.get("state")).isEqualTo("CANCELLED")).verifyComplete();
    }

    @Test
    void dosUsuariosNoMezclanEstadoNiSelecciones() {
        // Usuario A: hola -> 1 -> 1 (Compra -> Internet movil)
        send("WA", "a1", "hola");
        send("WA", "a2", "1");
        send("WA", "a3", "1");
        // Usuario B: hola -> 3 -> 2 (Quejas -> Cobertura)
        send("WB", "b1", "hola");
        send("WB", "b2", "3");
        send("WB", "b3", "2");

        StepVerifier.create(conversationRow("WA"))
                .assertNext(r -> assertThat(r.get("state")).isEqualTo("PRODUCT_MENU")).verifyComplete();
        StepVerifier.create(conversationRow("WB"))
                .assertNext(r -> assertThat(r.get("state")).isEqualTo("DETAIL_MENU")).verifyComplete();

        StepVerifier.create(hasSelection("WA", "INTERNET_MOBILE")).assertNext(b -> assertThat(b).isTrue()).verifyComplete();
        StepVerifier.create(hasSelection("WA", "COVERAGE")).assertNext(b -> assertThat(b).isFalse()).verifyComplete();
        StepVerifier.create(hasSelection("WB", "COVERAGE")).assertNext(b -> assertThat(b).isTrue()).verifyComplete();
        StepVerifier.create(hasSelection("WB", "INTERNET_MOBILE")).assertNext(b -> assertThat(b).isFalse()).verifyComplete();

        StepVerifier.create(count("SELECT COUNT(*) AS cnt FROM conversation WHERE wa_id = 'WA'")).assertNext(n -> assertThat(n).isEqualTo(1L)).verifyComplete();
        StepVerifier.create(count("SELECT COUNT(*) AS cnt FROM conversation WHERE wa_id = 'WB'")).assertNext(n -> assertThat(n).isEqualTo(1L)).verifyComplete();
    }

    @Test
    void wamidDuplicadoNoAvanzaDosVecesNiDuplicaOutbox() {
        send("W-Q", "id1", "hola");
        send("W-Q", "id2", "1");           // -> PURCHASE_MENU
        send("W-Q", "id2", "1");           // reintento del MISMO wamid: debe ignorarse
        StepVerifier.create(conversationRow("W-Q"))
                .assertNext(r -> assertThat(r.get("state")).isEqualTo("PURCHASE_MENU")).verifyComplete();
        // 2 procesos (hola + 1) y sus outboxes; el reintento no añade ninguno.
        StepVerifier.create(countOutbox()).assertNext(n -> assertThat(n).isEqualTo(2L)).verifyComplete();
        StepVerifier.create(countInbound()).assertNext(n -> assertThat(n).isEqualTo(2L)).verifyComplete();
    }

    @Test
    void dosMensajesConcurrentesDelMismoUsuarioNoSeCorrompen() throws Exception {
        send("W-R", "cc0", "hola");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> f1 = pool.submit(() -> postNoAssert("W-R", "cc1", "1"));
            Future<?> f2 = pool.submit(() -> postNoAssert("W-R", "cc2", "3"));
            f1.get();
            f2.get();
        } finally {
            pool.shutdown();
        }

        StepVerifier.create(conversationRow("W-R"))
                .assertNext(r -> assertThat((String) r.get("state")).isIn("PURCHASE_MENU", "COMPLAINT_MENU", "PRODUCT_MENU", "DETAIL_MENU"))
                .verifyComplete();
        StepVerifier.create(count("SELECT COUNT(*) AS cnt FROM conversation WHERE wa_id = 'W-R'"))
                .assertNext(n -> assertThat(n).isEqualTo(1L)).verifyComplete();
        StepVerifier.create(countInbound()).assertNext(n -> assertThat(n).isEqualTo(3L)).verifyComplete();
    }

    @Test
    void noSePuedeSaltarLaIdentificacionNiLaValidacion() {
        send("W-S", "j1", "hola");
        send("W-S", "j2", "1");            // Compra (N2) -> crea seleccion nivel 1
        send("W-S", "j3", "1");            // Internet movil (PRODUCT_MENU, N3) -> crea seleccion nivel 2
        // Intento de saltar la identificación escribiendo un documento directamente:
        // en PRODUCT_MENU eso no es valido -> sigue en PRODUCT_MENU, no crea seleccion N3.
        send("W-S", "j4", "1234567890");
        StepVerifier.create(conversationRow("W-S"))
                .assertNext(r -> assertThat(r.get("state")).isEqualTo("PRODUCT_MENU")).verifyComplete();
        // Nivel maximo alcanzado: 2 (PURCHASE en N1, INTERNET_MOBILE en N2).
        // No se crea seleccion de nivel 3 porque "1234567890" es opcion invalida en PRODUCT_MENU.
        StepVerifier.create(topLevel("W-S")).assertNext(lv -> assertThat(lv).isEqualTo(2)).verifyComplete();

        // Para llegar a CONFIRMATION_MENU hay que pasar por nombre, tipo y numero de documento:
        send("W-S", "j5", "1");            // 5 GB en PRODUCT_MENU -> NAME_INPUT (N4)
        send("W-S", "j6", "Elsa Vidal");   // nombre -> IDENTIFICATION_MENU (N4)
        send("W-S", "j7", "1");            // CC en IDENTIFICATION_MENU -> DOCUMENT_INPUT (N4)
        send("W-S", "j8", "1234567890");   // documento valido -> CONFIRMATION_MENU (N5)
        StepVerifier.create(conversationRow("W-S"))
                .assertNext(r -> assertThat(r.get("state")).isEqualTo("CONFIRMATION_MENU")).verifyComplete();
    }

    @Test
    void noSeLlegaAFinalSinConfirmacionExplicita() {
        send("W-T", "f1", "hola");
        send("W-T", "f2", "1");
        send("W-T", "f3", "1");
        send("W-T", "f4", "1");              // 5 GB -> NAME_INPUT
        send("W-T", "f5", "Team Dos");       // Nombre
        send("W-T", "f6", "1");              // CC
        send("W-T", "f7", "1234567890");     // -> CONFIRMATION (N5)
        StepVerifier.create(conversationRow("W-T"))
                .assertNext(r -> assertThat(r.get("state")).isEqualTo("CONFIRMATION_MENU")).verifyComplete();
        send("W-T", "f8", "4");              // Cancelar en N5 (no FINAL)
        StepVerifier.create(conversationRow("W-T"))
                .assertNext(r -> assertThat(r.get("state")).isEqualTo("CANCELLED")).verifyComplete();
    }

    @Test
    void cambiarPaqueteEnConfirmacionRegresaAlNivel3SinPerderInformacion() {
        send("W-U", "cp1", "hola");
        send("W-U", "cp2", "1");           // Compra
        send("W-U", "cp3", "1");           // Internet movil
        send("W-U", "cp4", "1");           // 5 GB -> NAME_INPUT
        send("W-U", "cp5", "Omar Gil");    // Nombre
        send("W-U", "cp6", "1");           // CC
        send("W-U", "cp7", "1234567890");  // -> CONFIRMATION
        send("W-U", "cp8", "2");           // Cambiar paquete -> PRODUCT_MENU (N3)
        StepVerifier.create(conversationRow("W-U"))
                .assertNext(r -> assertThat(r.get("state")).isEqualTo("PRODUCT_MENU")).verifyComplete();
        StepVerifier.create(hasSelection("W-U", "INTERNET_MOBILE")).assertNext(b -> assertThat(b).isTrue()).verifyComplete();
        StepVerifier.create(hasSelection("W-U", "5GB")).assertNext(b -> assertThat(b).isTrue()).verifyComplete();
    }

    @Test
    void asesorGeneraDobleMensajeYQuedaActivoConDespertador() {
        send("W-V", "ag1", "hola");
        send("W-V", "ag2", "1");
        send("W-V", "ag3", "1");
        send("W-V", "ag4", "1");           // 5 GB -> NAME_INPUT
        send("W-V", "ag5", "Vera Pardo");  // Nombre
        send("W-V", "ag6", "1");           // CC
        send("W-V", "ag7", "1234567890");  // -> CONFIRMATION
        send("W-V", "ag8", "3");           // Hablar con asesor -> HUMAN_AGENT (doble mensaje)
        // HUMAN_AGENT permanece ACTIVE (despertador INICIO/MENU), no CLOSED.
        StepVerifier.create(conversationRow("W-V"))
                .assertNext(r -> {
                    assertThat(r.get("state")).isEqualTo("HUMAN_AGENT");
                    assertThat(r.get("status")).isEqualTo("ACTIVE");
                }).verifyComplete();
        // 7 respuestas previas + cliente + admin = 9 outbox.
        StepVerifier.create(countOutbox()).assertNext(n -> assertThat(n).isEqualTo(9L)).verifyComplete();
        // Silencio permanente: mensaje suelto no genera outbox nuevo.
        send("W-V", "ag9", "gracias, sigo esperando");
        StepVerifier.create(countOutbox()).assertNext(n -> assertThat(n).isEqualTo(9L)).verifyComplete();
        // Despertador INICIO: vuelve a MAIN_MENU con menu interactivo.
        send("W-V", "ag10", "INICIO");
        StepVerifier.create(conversationRow("W-V"))
                .assertNext(r -> {
                    assertThat(r.get("state")).isEqualTo("MAIN_MENU");
                    assertThat(r.get("status")).isEqualTo("ACTIVE");
                }).verifyComplete();
        StepVerifier.create(countOutbox()).assertNext(n -> assertThat(n).isEqualTo(10L)).verifyComplete();
    }

    @Test
    void profileNameSeGuardaYPersonalizaBienvenida() {
        String payload = "{\"object\":\"waba\",\"entry\":[{\"id\":\"WABA\",\"changes\":[{"
                + "\"field\":\"messages\",\"value\":{\"contacts\":[{\"profile\":{\"name\":\"Jhonki\"}}],\"messages\":[{"
                + "\"from\":\"573009998887\",\"id\":\"wamid.profile-1\",\"type\":\"text\","
                + "\"text\":{\"body\":\"hola\"}}]}}]}]}";
        client.post()
                .uri("/webhook/whatsapp")
                .header("X-Hub-Signature-256", sign(SECRET, payload))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchange()
                .expectStatus().isOk();
        StepVerifier.create(databaseClient.sql("SELECT profile_name FROM conversation WHERE wa_id = '573009998887'")
                        .map(row -> row.get("profile_name", String.class)).one())
                .assertNext(name -> assertThat(name).isEqualTo("Jhonki")).verifyComplete();
        StepVerifier.create(databaseClient.sql("SELECT payload FROM outbox_message WHERE wa_id = '573009998887'")
                        .map(row -> row.get("payload", String.class)).one())
                .assertNext(pl -> assertThat(pl).contains("Jhonki")).verifyComplete();
    }

    // ----------------------------------------------------------------
    // Estado terminal: la conversación queda cerrada y el bot en silencio
    // (regresión del bucle de "mensajes fantasma" / menú principal repetido)
    // ----------------------------------------------------------------

    @Test
    void estadoFinalNoRespondeMensajesSueltosNiReabreLaConversacion() {
        long outboxBefore = finalizarCompra("W-W", "tw");

        // Mensajes tardíos que no son un reinicio explícito: NO deben generar
        // ninguna respuesta automática (fin del bucle de menús).
        send("W-W", "tw9", "gracias");
        send("W-W", "tw10", "1");
        send("W-W", "tw11", "menu opciones");

        StepVerifier.create(countOutbox())
                .assertNext(n -> assertThat(n).isEqualTo(outboxBefore)).verifyComplete();
        StepVerifier.create(conversationRow("W-W")).assertNext(r -> {
            assertThat(r.get("state")).isEqualTo("FINAL");
            assertThat(r.get("status")).isEqualTo("CLOSED");
        }).verifyComplete();
        StepVerifier.create(count("SELECT COUNT(*) AS cnt FROM conversation WHERE wa_id = 'W-W'"))
                .assertNext(n -> assertThat(n).isEqualTo(1L)).verifyComplete();
        // Los entrantes sí quedan registrados (auditoría + deduplicación): 8 del flujo + 3 sueltos.
        StepVerifier.create(countInbound()).assertNext(n -> assertThat(n).isEqualTo(11L)).verifyComplete();
    }

    @Test
    void saludoTrasFinalizarAbreNuevaConversacionActiva() {
        finalizarCompra("W-X", "rx");

        send("W-X", "rx9", "hola");

        StepVerifier.create(count("SELECT COUNT(*) AS cnt FROM conversation WHERE wa_id = 'W-X'"))
                .assertNext(n -> assertThat(n).isEqualTo(2L)).verifyComplete();
        StepVerifier.create(databaseClient.sql(
                                "SELECT state FROM conversation WHERE wa_id = 'W-X' AND status = 'ACTIVE'")
                        .map(row -> row.get("state", String.class))
                        .one())
                .assertNext(state -> assertThat(state).isEqualTo("MAIN_MENU")).verifyComplete();
        // 8 respuestas del flujo anterior + la bienvenida de la nueva conversación.
        StepVerifier.create(countOutbox()).assertNext(n -> assertThat(n).isEqualTo(9L)).verifyComplete();
    }

    @Test
    void estadoCanceladoTambienQuedaEnSilencio() {
        send("W-Y", "cy1", "hola");
        send("W-Y", "cy2", "cancelar");
        send("W-Y", "cy3", "1");     // mensaje suelto posterior: sin respuesta

        StepVerifier.create(conversationRow("W-Y")).assertNext(r -> {
            assertThat(r.get("state")).isEqualTo("CANCELLED");
            assertThat(r.get("status")).isEqualTo("CLOSED");
        }).verifyComplete();
        StepVerifier.create(countOutbox()).assertNext(n -> assertThat(n).isEqualTo(2L)).verifyComplete();
    }

    /** Ejecuta un flujo de compra completo hasta FINAL y devuelve el total de outbox. */
    private long finalizarCompra(String waId, String prefix) {
        send(waId, prefix + "1", "hola");
        send(waId, prefix + "2", "1");           // Compra -> PURCHASE_MENU
        send(waId, prefix + "3", "1");           // Internet móvil -> PRODUCT_MENU
        send(waId, prefix + "4", "1");           // 5 GB -> NAME_INPUT
        send(waId, prefix + "5", "Test User");   // Nombre -> IDENTIFICATION_MENU
        send(waId, prefix + "6", "1");           // CC -> DOCUMENT_INPUT
        send(waId, prefix + "7", "1234567890");  // Documento -> CONFIRMATION_MENU
        send(waId, prefix + "8", "1");           // Confirmar -> FINAL

        StepVerifier.create(conversationRow(waId)).assertNext(r -> {
            assertThat(r.get("state")).isEqualTo("FINAL");
            assertThat(r.get("status")).isEqualTo("CLOSED");
        }).verifyComplete();
        return countOutbox().block();
    }

    @Test
    void rollbackTecnicoNoDejaEstadoParcial() {
        TransactionalOperator txOp = applicationContext.getBean(TransactionalOperator.class);
        ConversationRepository convRepo = applicationContext.getBean(ConversationRepository.class);
        OutboxRepository outboxRepo = applicationContext.getBean(OutboxRepository.class);

        // Preparar: conversación + mensaje saliente + su outbox YA presente.
        UUID convId = UUID.randomUUID();
        UUID msgId = UUID.randomUUID();
        databaseClient.sql("""
                        INSERT INTO conversation (id, wa_id, state, status, version, created_at, updated_at)
                        VALUES (:id, 'W-RB', 'MAIN_MENU', 'ACTIVE', 0, now(), now())
                        """).bind("id", convId).then().block();
        databaseClient.sql("""
                        INSERT INTO message (id, conversation_id, direction, status, type, content, created_at)
                        VALUES (:id, :cid, 'OUTBOUND', 'PENDING', 'TEXT', 'x', now())
                        """).bind("id", msgId).bind("cid", convId).then().block();
        databaseClient.sql("""
                        INSERT INTO outbox_message
                            (id, message_id, conversation_id, wa_id, payload, status, attempts,
                             next_attempt_at, created_at, updated_at)
                        VALUES (:id, :mid, :cid, 'W-RB', '{}', 'PENDING', 0, now(), now(), now())
                        """).bind("id", UUID.randomUUID()).bind("mid", msgId).bind("cid", convId).then().block();

        // En UNA transacción: actualizamos la conversación (nuevo estado) y luego el save
        // del outbox falla por uq_outbox_message_id (message_id ya usado). Debe haber rollback.
        txOp.execute(tx -> convRepo.findActiveByWaIdForUpdate("W-RB")
                .flatMap(c -> convRepo.update(c.withState(ConversationState.PURCHASE_MENU)))
                .then(outboxRepo.save(OutboxMessage.pendingFor(msgId, convId, "W-RB", "{}", java.time.Instant.now()))))
                .then()
                .as(StepVerifier::create)
                .expectError()
                .verify();

        // El rollback revirtió el UPDATE: la conversación sigue en MAIN_MENU.
        StepVerifier.create(conversationRow("W-RB"))
                .assertNext(r -> assertThat(r.get("state")).isEqualTo("MAIN_MENU")).verifyComplete();
        // No quedó un outbox huérfano adicional para esa conversación (solo el pre-cargado).
        StepVerifier.create(count("SELECT COUNT(*) AS cnt FROM outbox_message WHERE conversation_id = '" + convId + "'"))
                .assertNext(n -> assertThat(n).isEqualTo(1L)).verifyComplete();
    }
}
