# Arquitectura — WhatsApp Call Center Bot

## 1. Propósito

Documento de referencia de la arquitectura del bot de atención al cliente por WhatsApp (WhatsApp Cloud API de Meta). Define capas, componentes, flujo de procesamiento, concurrencia y manejo de errores. Es la fuente de verdad para las fases de implementación.

## 2. Contexto

```
                    ┌────────────────────────────────────────────────┐
                    │               WhatsApp (Meta)                  │
                    │        Cloud API  graph.facebook.com/vX.X      │
                    └───────▲───────────────────────────────┬────────┘
                    Webhook │  (POST mensajes / GET verify)  │ (POST send)
                    GET/POST│                              │
                    ┌───────┴───────────────────────────────▼────────┐
                    │             backend (WebFlux + R2DBC)           │
                    │ ┌────────────────────────────────────────────┐ │
                    │ │   Conversational Engine (state machine)    │ │
                    │ │   domain puro: sin conocimiento de canal   │ │
                    │ └────────────────────────────────────────────┘ │
                    │             ▲                  │                │
                    │  persist |  R2DBC            | enviar          │
                    └─────┬───────────────┬───────────────────────────┘
                          │               │   (port → adaptadores)
                    ┌─────▼─────┐   ┌─────▼─────────────────────────┐
                    │ PostgreSQL│   │ MockWhatsAppClient (dev/test) │
                    │           │   │ MetaWhatsAppClient (prod)     │
                    └───────────┘   └───────────────────────────────┘
```

## 3. Principios rectores

- **KISS primero**: capas claras sin abstracciones innecesarias.
- **Dominio desacoplado**: el motor conversacional no conoce WhatsApp; conoce estados, entradas y respuestas.
- **Reactivo de extremo a extremo** dentro de la responsabilidad del backend (WebFlux + R2DBC + WebClient).
- **Estado explícito**: el estado de la conversación es datos persistentes, no variables en memoria.
- **Extensible por adición**: nuevos menús, canales o clientes se agregan sin modificar el núcleo (Open/Closed).

## 4. Decisiones fundamentales y justificación

| Decisión | Justificación |
|---|---|
| Spring WebFlux | Carga dominante es I/O (webhook, llamadas a Meta, base de datos). El modelo reactivo no bloquea hilos bajo ráfagas de mensajes y permite backpressure. Cumple el requisito de programación reactiva. |
| R2DBC | Única vía de persistencia no bloqueante compatible con WebFlux. Requisito explícito: sin JPA ni JDBC bloqueante. |
| PostgreSQL | Modelo relacional natural (conversación → selecciones → mensajes). Soporta `FOR UPDATE`, índices parciales y JSONB. ACID para transiciones de estado. |
| Máquina de estados | Reemplaza cadenas de if/else por transiciones explícitas; cada estado define qué entradas acepta y a dónde va. |
| Ports & Adapters (hexagonal ligero) | Desacopla WhatsApp, persistencia y motor. Permite desarrollar con mock y conectar Meta sin tocar el núcleo. |
| Clean Architecture adaptada | Tres capas (domain / application / infrastructure) en un solo módulo Maven. Suficiente para el alcance; evita sobreingeniería multi-módulo. |

## 5. Capas y responsabilidades

| Capa | Responsabilidad | Prohibido |
|---|---|---|
| **domain** | Modelo (Conversation, Selection, Message, ConversationState), motor conversacional (handlers por estado), validadores de dominio, puertos (interfaces) | Depender de Spring, R2DBC o HTTP |
| **application** | Casos de uso: orquestar mensaje entrante (recibir → procesar → persistir → enviar), deduplicación | Lógica de negocio pura; solo coordinación |
| **infrastructure** | WebhookController (entrada HTTP), clientes WhatsApp (Meta/Mock), persistencia R2DBC, verificación de firma, OutboxPoller | Reglas de negocio |
| **config** | Beans, perfiles, Flyway, R2DBC, WebClient, seguridad | — |

## 6. Componentes principales

| Componente | Capa | Responsabilidad |
|---|---|---|
| `WebhookController` | infra | GET verify (challenge) y POST de eventos de Meta. Solo **Fase A**: responde 200 tras el COMMIT (procesamiento duradero). |
| `WebhookSignatureVerifier` | infra | Valida `X-Hub-Signature-256` (HMAC-SHA256 con app_secret; comparación en tiempo constante). |
| `InboundMessageOrchestrator` | application | Orquesta la Fase A: dedupe → transacción R2DBC (cargar/crear conversación, engine, estado+selecciones) → insert mensaje saliente + outbox → responder 200. Si la conversación está en estado terminal (`FINAL`/`CANCELLED`) y el mensaje no reinicia explícitamente, solo registra el entrante: **no hay respuesta** (silencio). |
| `ConversationEngine` | domain | Resuelve el handler según el estado actual y ejecuta la transición. |
| `ConversationStateHandler` (uno por estado) | domain | Valida la entrada, produce respuesta, siguiente estado y selección a registrar. Registro tipo `Map<State, Handler>`. |
| `ConversationRepository` (port) | domain | Operaciones de persistencia de conversación, selecciones y mensajes. |
| `WhatsAppClient` (port) | domain | `sendMessage(...)`; abstracción del canal. |
| `MetaWhatsAppClient` | infra | WebClient → Graph API (Bearer token, phone_number_id). |
| `MockWhatsAppClient` | infra | Dev/test: registra y simula respuestas; permite E2E sin Meta. |
| Repositorios R2DBC | infra | Adaptadores de `ConversationRepository` sobre PostgreSQL. |
| `OutboxPoller` | infra | Scheduler reactivo (mismo proceso): reclama `outbox_message` `PENDING` con `SKIP LOCKED`, envía por `WhatsAppClient` y confirma `SENT`. |

## 7. Pipeline de procesamiento (mensaje entrante)

El procesamiento se divide en DOS tiempos por diseño:

- **Fase A — Ingesta (síncrona, dentro del request de Meta):** recepción, validación, deduplicación, procesamiento y **durabilidad** de la respuesta.
- **Fase B — Entrega (asíncrona, worker interno del backend):** envío a WhatsApp mediante el patrón **Outbox**.

Esta separación elimina el riesgo de "mensaje procesado pero respuesta perdida": el estado *procesado* y la *respuesta pendiente de enviar* se persisten en la MISMA transacción (ver sección 8).

### Fase A — Ingesta (lo que ocurre ANTES del 200)

1. Meta hace POST al webhook con el **body crudo**.
2. Se valida `X-Hub-Signature-256`; si falla → 400 y no se procesa.
3. Se extraen `wa_id`, `wamid` y texto (se ignoran `statuses`, mensajes no textuales y mensajes vacíos → 200 sin más).
4. **Transacción R2DBC real y única** (todas las operaciones sobre la misma conexión; el lock se mantiene hasta el COMMIT):
   a. `INSERT` del mensaje entrante con status `RECEIVED`.
   b. `SELECT ... FOR UPDATE` de la conversación activa del `wa_id` (o `INSERT` si no existe; el índice único parcial impide dos activas).
   c. **Deduplicación**: `UPDATE message SET status='PROCESSED' WHERE id = ? AND status = 'RECEIVED'`. Si afecta 0 filas, el mensaje ya fue procesado → se aborta la transacción y se responde 200 sin nueva respuesta.
   d. El `ConversationEngine` procesa la entrada con el estado actual → `(texto de respuesta, nuevo estado, selección)`.
   e. `UPDATE conversation` (nuevo estado, `version = version + 1`) con cláusula `WHERE version = ?`.
   f. `UPSERT` en `conversation_selection` (único por `(conversation_id, level)`).
   g. `INSERT` del mensaje saliente con status `PENDING`.
   h. `INSERT` de `outbox_message` con status `PENDING` (payload = texto de respuesta).
   i. `COMMIT`. A partir de este punto el procesamiento está **confirmado** y la respuesta está **durabilizada**: no puede perderse.
5. Se responde **200 al webhook**.

### Fase B — Entrega (lo que ocurre DESPUÉS del 200)

6. El `OutboxPoller` (scheduler reactivo del mismo proceso; sin brokers externos) reclama en lotes:

   `SELECT ... FROM outbox_message WHERE status='PENDING' AND next_attempt_at <= now() ORDER BY created_at LIMIT N FOR UPDATE SKIP LOCKED`

   dentro de una transacción.
7. Marca la fila como `SENDING` y fija `lease_expires_at = now() + 60s`.
8. Envía vía `WhatsAppClient` (Mock o Meta) con `WebClient` reactivo, timeout y backoff.
9. Éxito → transacción: `outbox_message` a `SENT` (+`sent_at`) y el mensaje saliente a `SENT` (+`wa_message_id` devuelto por Meta).
10. Fallo transitorio → la fila vuelve a `PENDING`, `attempts++` y `next_attempt_at = now() + backoff(2^attempts, tope 60s)`.
11. Fallo permanente (intentos agotados) → `FAILED` (outbox y mensaje saliente); no se borra nada: queda para auditoría y reenvío manual.

### Significado del 200

El 200 del webhook significa exactamente: *"mensaje recibido, validado, procesado y su respuesta está segura en la cola de salida (Outbox)"*. **NO** significa "entregado al usuario". La entrega la garantiza la Fase B.

## 8. Entrega confiable de respuestas (Patrón Outbox)

### Problema que resuelve

En el flujo anterior, si la aplicación fallaba entre el COMMIT y el envío, Meta reintentaba el webhook, pero el sistema detectaba el `wamid` como `PROCESSED` y respondía 200 sin reenviar → **la respuesta se perdía**.

### Solución

La salida al canal se convierte en un **efecto persistido dentro de la misma transacción** que procesa la entrada: la única forma de "confirmar" un mensaje entrante es dejar su respuesta durablemente encolada en `outbox_message`. El envío queda a cargo de un poller interno sobre PostgreSQL (sin Kafka / RabbitMQ / Redis).

### Estados de los mensajes

**Entrante (`message.direction='INBOUND'`):**
- `RECEIVED` → persistido y aún no procesado.
- `PROCESSED` → procesado **y** su respuesta ya está en el Outbox (ambos se confirman en el MISMO COMMIT). Es el único momento en que se confirma el procesamiento.

**Saliente (`message.direction='OUTBOUND'`):**
- `PENDING` → respuesta generada, esperando envío.
- `SENT` → confirmado por el adaptador (Meta devolvió `wamid`, o Mock respondió ok).
- `FAILED` → reintentos agotados.

**Outbox (`outbox_message.status`):**
- `PENDING` → listo para intentar.
- `SENDING` → reclamado por el poller (lease activo de 60s).
- `SENT` → entregado.
- `FAILED` → agotó reintentos (auditoría / reenvío manual).

### Cuándo ocurre cada cosa

| Evento | Momento |
|---|---|
| Se persiste el mensaje entrante | Fase A, paso 4a (dentro de la transacción) |
| Se crea el Outbox | Fase A, paso 4h — siempre en el mismo COMMIT que marca `PROCESSED` |
| Se confirma el procesamiento | El COMMIT de la Fase A (paso 4i) |
| Primer intento de envío | Próximo tick del poller (intervalo configurable, p. ej. 2 s) |
| Reintentos | Backoff exponencial (2^n, tope 60 s) hasta N intentos |
| Confirmación de entrega | Fase B, paso 9 (`SENT`) |

### No perder ni duplicar

- **No se pierde una respuesta**: si la app muere después del COMMIT, la fila `PENDING` permanece en PostgreSQL y el poller la envía al recuperarse. Si muere ANTES del COMMIT, hay rollback total (el entrante sigue `RECEIVED`) y el reintento de Meta reprocesa limpio. Si muere DURANTE el envío, la fila queda `SENDING` con lease; el poller recupera filas `SENDING` con `lease_expires_at` vencido y reintenta.
- **No se duplica el procesamiento**: el paso 4c (`UPDATE RECEIVED→PROCESSED`) es idempotente y forma el *exactly-once process*; el índice único de `wa_message_id` es la red de seguridad.
- **No se duplica el envío**: el reclamo `PENDING→SENDING` con `FOR UPDATE SKIP LOCKED` solo es visible por un worker; el índice único `outbox_message.message_id` impide dos filas de cola para la misma respuesta. La entrega es *at-least-once*: en el caso extremadamente raro de crash justo después del POST a Meta, el usuario podría recibir el mensaje dos veces (limitación aceptada y documentada).

## 9. Estrategia de concurrencia

- **Fuente de verdad**: PostgreSQL. Ningún estado vive en memoria; cada transición es una transacción.
- **Aislamiento por `wa_id`**: cada operación se keyea por usuario; existe UNA conversación activa por usuario (índice único parcial sobre `conversation`).
- **Serialización por conversación**: dentro de la Fase A, el `SELECT ... FOR UPDATE` de la conversación se ejecuta en una **transacción R2DBC real** (misma conexión para SELECT + UPDATE + INSERT; el lock se libera en COMMIT/ROLLBACK). Dos mensajes simultáneos del mismo usuario se serializan a nivel de fila: el segundo espera hasta que el primero haga COMMIT.
  - **Importante**: con R2DBC, ejecutar `SELECT ... FOR UPDATE` fuera de una transacción (operaciones sueltas en conexiones distintas) NO mantiene el lock. Toda la Fase A se ejecuta con `TransactionalOperator` / `DatabaseClient.inTransaction` de principio a fin, y el flujo reactivo se completa (COMMIT) antes de responder 200.
- **Optimistic locking**: columna `version`; `UPDATE ... WHERE version = ?` con reintento acotado. Mantiene valor como segunda línea de defensa ante escrituras concurrentes imprevistas (no sustituye al `FOR UPDATE`).
- **Deduplicación**: `wa_message_id` único + transición idempotente `RECEIVED → PROCESSED` (paso 4c).
- **Workers de salida**: el `OutboxPoller` usa `FOR UPDATE SKIP LOCKED` en su propia transacción para que dos ticks o instancias no reclamen la misma fila.
- **Escalado horizontal (futuro)**: con multi-instancia, `FOR UPDATE` / `SKIP LOCKED` sobre PostgreSQL siguen funcionando (el lock es de BD, no de proceso); los schedulers de cada instancia quedan cubiertos por el lease. Redis solo se evaluaría si el Outbox llegara a ser el cuello de botella (fuera de alcance).

## 10. Manejo de errores

| Categoría | Estrategia |
|---|---|
| Firma inválida | 400 al webhook + log de advertencia (posible ataque) |
| Payload inesperado / mensaje vacío | Se ignora y se responde 200 (no provocar reintentos innecesarios de Meta) |
| Error de BD o timeout en la Fase A | 500/503 al webhook → Meta reintentará; rollback total; el dedupe evita doble procesamiento |
| Fallo de envío transitorio (429 / 5xx / red) | Reintentos del `OutboxPoller` con backoff exponencial hasta N intentos |
| Fallo de envío permanente | `FAILED` en outbox y mensaje saliente + log estructurado sin datos sensibles; reenvío manual desde auditoría |
| Entrada inválida (negocio) | El handler responde re-prompt y permanece en el mismo estado |

Nota: la entrega de respuestas ya NO depende del reintento de Meta: el webhook responde 200 una vez la respuesta está durable en el Outbox.

## 11. Extensibilidad

| Necesidad | Cómo se satisface sin reescribir el núcleo |
|---|---|
| Nuevo menú / submenú | Nuevo estado + nuevo `ConversationStateHandler` registrado en el registry |
| Nueva categoría principal | Mismo patrón: estado de nivel 2 + handlers |
| Nuevo canal (Telegram, etc.) | Nueva implementación del port `WhatsAppClient` + adaptador de entrada |
| Conectar Meta real | Activar `MetaWhatsAppClient` (ya previsto) mediante variables de entorno; el engine no cambia |
| Refinar textos / opciones | Seed/config de menús aislado del motor |

## 12. Estructura de directorios del backend

```
backend/
├── pom.xml
└── src/
    ├── main/java/com/botwap/
    │   ├── BotApplication.java
    │   ├── config/                # Beans, perfiles, Flyway, R2DBC, WebClient
    │   ├── domain/
    │   │   ├── model/             # Conversation, Selection, Message, ConversationState
    │   │   ├── port/              # ConversationRepository, WhatsAppClient
    │   │   ├── engine/            # ConversationEngine, StateHandler (interfaz), registry, handlers
    │   │   └── validator/         # DocumentValidator, MenuOptionParser, InputNormalizer
    │   ├── application/
    │   │   ├── service/           # InboundMessageOrchestrator, WebhookVerifier
    │   │   └── exception/         # Errores de dominio
    │   └── infrastructure/
    │       ├── web/               # WebhookController, DTOs, WebhookSignatureVerifier
    │       ├── whatsapp/          # MetaWhatsAppClient, MockWhatsAppClient, DTOs de payload
    │       ├── persistence/       # Entities R2DBC, repositorios reactivos, mappers
    │       └── outbox/            # OutboxPoller (scheduler reactivo) y lecturas de cola
    ├── main/resources/
    │   ├── application.yml        # + application-{local,test,meta}.yml
    │   └── db/migration/          # Flyway V1__init.sql, ...
    └── test/java/com/botwap/      # tests unitarios + integración

database/      # notas de referencia del esquema (lo canónico vive en Flyway del backend)
docs/          # documentación de arquitectura (este repositorio)
docker-compose.yml
.env.example
.gitignore
README.md
```

## 13. Límites y no-metas

- Una instancia del backend en esta fase (sin Redis; la serialización y el Outbox viven en PostgreSQL).
- Persistencia únicamente en PostgreSQL.
- Respuestas de texto plano (sin plantillas ni botones interactivos en la primera iteración).
- La identificación por documento es de negocio, no de seguridad (el usuario final no se autentica).
- `webhook_event` (payload crudo) queda como evolución futura: la auditoría inicial usa `message` + `conversation` + `conversation_selection` + `outbox_message`.