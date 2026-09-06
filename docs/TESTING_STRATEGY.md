# Estrategia de Testing — WhatsApp Call Center Bot

## 1. Pirámide de pruebas

```
        ┌──────────────┐  WebTestClient (E2E de webhook con PostgreSQL real)
        │  Integración │  + Testcontainers + MockWhatsAppClient
       ─┼──────────────┼─
      ┌─┴──────────────┴─┐  Handlers de estados, validadores,
      │  Unitarias       │  engine y conversión DTOs (rápidas, aisladas)
     ┌┴──────────────────┴┐
     │  (pocas, críticas)  │  Contratos de flujo y navegación
     └─────────────────────┘
```

## 2. Stack

| Herramienta | Uso |
|---|---|
| JUnit 5 + AssertJ | Base de pruebas unitarias |
| Reactor `StepVerifier` | Aserciones sobre flujos reactivos (`Mono`/`Flux`) |
| Mockito | Doblado de dependencias externas (repositorios) en unitarias |
| WebTestClient | Tests de integración del endpoint webhook |
| Testcontainers | PostgreSQL real (R2DBC) en tests de integración (requiere Docker) |
| MockWebServer (OkHttp) | Stub del endpoint de Graph API (para probar `MetaWhatsAppClient` sin red) |

## 3. Qué es unitario vs integración

**Unitarias (rápidas, sin Spring, sin BD):**
- Cada `ConversationStateHandler`: entrada válida → estado siguiente + respuesta correcta; entrada inválida → re-prompt y mismo estado.
- `DocumentValidator`: formato/reglas por tipo de documento (C.C., Pasaporte, NIT, cliente nuevo).
- `MenuOptionParser` / `InputNormalizer`: normalización de "HOLA ", "hola   ", comandos globales, trims.
- Registry del engine: resolución de handler por estado; estado sin handler → error controlado.
- Regla de no-atajo: desde `MAIN_MENU` no existe transición directa a `FINAL` / `CONFIRMATION_MENU` / respuesta final.

**Integración (Spring Boot test + Testcontainers + WebTestClient + MockWhatsAppClient):**
- Round-trip completo del webhook: POST payload de Meta → respuesta de texto esperada → estado persistido en BD.
- Deduplicación por `wamid` (mismo POST dos veces → una sola respuesta).
- Aislamiento entre dos usuarios simultáneos.
- Reanudación: conversación existente continúa desde estado almacenado (dos POST separados simulando la sesión).
- Webhook GET de verificación (`challenge` correcto/incorrecto).
- Firma inválida → 400.
- Recarga desde BD tras reinicio de la aplicación (estado durable).
- **Outbox** (con un `WhatsAppClient` simulado configurable para fallar): 200 sin envío, reintentos con backoff, lease/recuperación de `SENDING` huérfano y dedupe de envío entre ticks.

## 4. Los 10 tests obligatorios

| # | Test | Tipo |
|---|---|---|
| 1 | "hola" muestra menú principal | Integración (round-trip) + unitaria del handler por separado |
| 2 | Seleccionar Compra de paquetes lleva al menú correcto | Integración |
| 3 | La opción 3 dentro de Compra NO lleva a Quejas | Unitaria (transición de `PURCHASE_MENU`) + integración |
| 4 | Opción inválida genera respuesta apropiada y se mantiene el estado | Unitaria + integración |
| 5 | No se puede obtener la respuesta final antes del nivel 5 | Unitaria (grafo de estados: sin ruta directa) + integración (saltar pasos) |
| 6 | El documento se valida correctamente | Unitaria (`DocumentValidator`, casos borde: vacío, letras, corto, NIT con dígitos) |
| 7 | Cancelar reinicia o finaliza según diseño | Integración (estado → `CANCELLED` → un nuevo "hola" inicia nueva conversación) |
| 8 | Dos usuarios conversan simultáneamente sin mezclar estados | Integración (intercalar mensajes de A y B, verificar estados y respuestas) |
| 9 | Conversación existente continúa desde estado almacenado | Integración (POST 1: "hola"→"1"; POST 2: "1" → responde como `PURCHASE_MENU`) |
| 10 | Un nuevo "hola" reinicia correctamente una conversación | Integración (en estado avanzado → `menu`/reinicio limpia selecciones) |

### 4.1 Tests adicionales del patrón Outbox (entrega confiable)

| # | Test | Verifica |
|---|---|---|
| 11 | El webhook devuelve 200 aunque el envío falle | El 200 depende del COMMIT de la Fase A (outbox `PENDING`), no del envío |
| 12 | El poller reintenta con backoff y eventualmente entrega | Enviador simulado que falla 2 veces → `attempts=1,2` → éxito → `SENT` |
| 13 | Crash recovery del outbox | Fila `SENDING` con `lease_expires_at` vencido → el poller la regresa a `PENDING` y la envía |
| 14 | No se duplica el envío | Dos ticks simultáneos → una sola fila entregada (SKIP LOCKED + `PENDING→SENDING`) |
| 15 | Procesamiento exactamente una vez | Reintento de Meta con el mismo `wamid` → una sola fila de outbox creada |
| 16 | La respuesta no se pierde en crash post-commit | Simular caída tras commit: fila `PENDING` persistida → el poller la entrega al arrancar |
| 17 | Envío marcado `SENT` con el `wamid` devuelto | El outbound guarda el `wa_message_id` de la confirmación de Meta |

## 5. Casos borde adicionales

- Mensajes vacíos → sin respuesta y sin error.
- Texto inesperado en estado de selección → re-prompt.
- `volver` fuera de contexto (nivel 1) → mensaje informativo.
- Número de documento con espacios/guiones → normalización.
- Concurrencia: dos mensajes simultáneos del mismo usuario → una sola transición serializada (test integración con posts paralelos).
- Reintento de webhook (mismo `wamid`) → sin duplicados.

## 6. Configuración de tests de integración

- Perfil `test`: `MockWhatsAppClient` activo; PostgreSQL vía Testcontainers (misma imagen que compose: `postgres:16-alpine`).
- Flyway corre las migraciones sobre el contenedor antes de cada suite.
- `WebTestClient.bindToApplicationContext` o arranque real con `@SpringBootTest(webEnvironment = RANDOM_PORT)`.

## 7. Anti-patrones a evitar

- Tests que dependen de red real (nunca llamar a Meta en CI).
- Assertions sobre texto exacto cuando el texto es cosmético: priorizar verificar **estado resultante** y **selecciones persistidas**.
- Depender de timing (nunca `sleep`): usar `StepVerifier` y virtual time.

## 8. CI (recomendado)

- GitHub Actions: job con `java 21`, `mvn verify` (Testcontainers requiere Docker runner).
- Separar `mvn test` (unitarias, sin Docker) de las de integración si el runner no tiene Docker.