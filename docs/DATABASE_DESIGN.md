# Diseño de Base de Datos — WhatsApp Call Center Bot

## 1. Modelo entidad-relación

```
conversation (1) ──── (N) conversation_selection
      │
      ├────── (N) message  (entrantes y salientes)
      │           └────── (1:1) outbox_message  (cola de salida confiable — Outbox)
```

Razonamiento: una conversación es el contexto de estado; las selecciones registran la navegación por niveles; los mensajes registran el intercambio completo (auditoría y reconstrucción); `outbox_message` es la cola de envío confiable (patrón Outbox) que garantiza que ninguna respuesta se pierda.

## 2. Tablas

### 2.1 conversation

Contexto de estado de un usuario. UNA activa por `wa_id`.

| Columna | Tipo | Restricciones |
|---|---|---|
| id | UUID (PK) | generado por app o db |
| wa_id | VARCHAR(20) | NOT NULL — número del usuario (sin `+`) |
| state | VARCHAR(50) | NOT NULL — enum del estado actual (ej. `MAIN_MENU`) |
| status | VARCHAR(20) | NOT NULL — `ACTIVE` / `CLOSED` |
| version | BIGINT | NOT NULL DEFAULT 0 — optimistic lock |
| created_at | TIMESTAMPTZ | NOT NULL |
| updated_at | TIMESTAMPTZ | NOT NULL |
| closed_at | TIMESTAMPTZ | nullable |

**Índices y restricciones**
- PK: `id`.
- Índice único parcial: `UNIQUE (wa_id) WHERE status = 'ACTIVE'` → garantiza a nivel de BD una sola conversación activa por usuario.
- Índice por `status`, `updated_at` (consultas de administración).

### 2.2 conversation_selection

Una fila por nivel navegado en la conversación.

| Columna | Tipo | Restricciones |
|---|---|---|
| id | UUID (PK) | — |
| conversation_id | UUID | FK → conversation(id) `ON DELETE CASCADE` |
| level | SMALLINT | NOT NULL — 1..5 |
| state_key | VARCHAR(50) | NOT NULL — estado donde se eligió (para contexto) |
| option_key | VARCHAR(50) | NOT NULL — clave semántica (ej. `INTERNET_MOBILE`) |
| display_label | VARCHAR(120) | NOT NULL — texto para confirmación (ej. "Internet móvil") |
| metadata | JSONB | nullable — datos extra (ej. monto, número destino) |
| selected_at | TIMESTAMPTZ | NOT NULL |

**Índices y restricciones**
- PK: `id`.
- Único: `UNIQUE (conversation_id, level)` → un nivel, una selección; navegar de vuelta hace *upsert*.
- Índice: `conversation_id` (FK).

### 2.3 message

Registro de cada mensaje entrante y saliente (auditoría + deduplicación).

| Columna | Tipo | Restricciones |
|---|---|---|
| id | UUID (PK) | — |
| conversation_id | UUID | FK → conversation(id) `ON DELETE CASCADE` |
| wa_message_id | VARCHAR(64) | nullable — `wamid` de WhatsApp (solo si Meta lo provee) |
| direction | VARCHAR(10) | NOT NULL, CHECK `IN ('INBOUND','OUTBOUND')` |
| status | VARCHAR(20) | NOT NULL — inbound: `RECEIVED`/`PROCESSED`; outbound: `PENDING`/`SENT`/`FAILED` |
| type | VARCHAR(20) | NOT NULL — `TEXT`, `BUTTON`, etc. |
| content | TEXT | NOT NULL — texto del mensaje |
| created_at | TIMESTAMPTZ | NOT NULL |
| sent_at | TIMESTAMPTZ | nullable |

**Índices y restricciones**
- PK: `id`.
- Único: `wa_message_id` (parcial, donde es NOT NULL) → **deduplicación** ante reintentos de Meta.
- Índice: `(conversation_id, created_at)` → reconstrucción/auditoría cronológica.
- CHECK: `direction`, `status`.

### 2.4 outbox_message (cola de salida — patrón Outbox)

Una fila = una respuesta pendiente de enviar a WhatsApp. Se `INSERT` en la **misma transacción** que procesa el mensaje entrante (Fase A). El envío lo realiza el `OutboxPoller` (Fase B).

| Columna | Tipo | Restricciones |
|---|---|---|
| id | UUID (PK) | — |
| message_id | UUID | FK → message(id) — el mensaje saliente asociado; **ÚNICO** (un outbox por respuesta) |
| conversation_id | UUID | FK → conversation(id) `ON DELETE CASCADE` |
| wa_id | VARCHAR(20) | NOT NULL — destinatario (denormalizado para diagnóstico) |
| payload | JSONB | NOT NULL — documento de entrega (texto de la respuesta) |
| status | VARCHAR(20) | NOT NULL — `PENDING` / `SENDING` / `SENT` / `FAILED` |
| attempts | SMALLINT | NOT NULL DEFAULT 0 — número de reintentos |
| next_attempt_at | TIMESTAMPTZ | NOT NULL — control del backoff (el poller solo reclama `<= now()`) |
| lease_expires_at | TIMESTAMPTZ | nullable — vence el lease de `SENDING` (recuperación tras crash) |
| last_error | VARCHAR(255) | nullable — último error (sin datos sensibles) |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL |
| sent_at | TIMESTAMPTZ | nullable — confirmación de entrega |

**Índices y restricciones**
- PK: `id`.
- **Único**: `message_id` → imposible duplicar el envío de una misma respuesta.
- Índice de cola: `(status, next_attempt_at)` — el poller consulta `status='PENDING' AND next_attempt_at <= now() ORDER BY created_at` con `FOR UPDATE SKIP LOCKED`.
- Índices por `conversation_id` y `wa_id` (diagnóstico).

### 2.5 webhook_event (FUERA DEL ALCANCE INICIAL)

En la primera iteración **NO se implementa**. La auditoría inicial se cubre con `message` + `conversation` + `conversation_selection` + `outbox_message`.

**Evolución futura** (cuando se necesite forense completo o *replay* manual de eventos de Meta):

| Columna | Tipo | Restricciones |
|---|---|---|
| id | UUID (PK) | — |
| raw_payload | JSONB | NOT NULL — payload crudo de Meta |
| received_at | TIMESTAMPTZ | NOT NULL |
| conversation_id | UUID | nullable FK |

No se crea en Fase 3.

## 3. Concurrencia, integridad y ciclo de vida de mensajes

### 3.1 Ciclo de vida de estados

```
ENTRANTE (message INBOUND):  RECEIVED ──[transacción Fase A]──▶ PROCESSED
                                        (el COMMIT deja su respuesta
                                         ya encolada en outbox)

SALIENTE (message OUTBOUND): PENDING ──[envío OK]──▶ SENT
                                  └──[intentos agotados]──▶ FAILED

OUTBOX:  PENDING ──[poller reclama]──▶ SENDING ──[envío OK]──▶ SENT
               └──[fallo transitorio]──▶ PENDING (attempts++, backoff)
               └──[intentos agotados]──▶ FAILED
         SENDING ──[lease vencido]──▶ PENDING (crash recovery)
```

Reglas:
- **El procesamiento se confirma SOLO en el COMMIT** que a la vez marca el entrante `PROCESSED` y crea la fila `outbox` `PENDING`. Nunca existe "procesado sin respuesta en cola".
- El `UPDATE entrante RECEIVED→PROCESSED` es idempotente (`WHERE status='RECEIVED'`): si afecta 0 filas, el mensaje ya se procesó → se responde 200 sin duplicar.

### 3.2 Garantías de concurrencia

| Riesgo | Estrategia |
|---|---|
| Dos mensajes simultáneos del mismo usuario | `SELECT ... FOR UPDATE` sobre `conversation` DENTRO de la transacción de la Fase A → el segundo se serializa hasta el COMMIT del primero |
| Actualización concurrente del estado | Optimistic lock `version`: `UPDATE ... WHERE id=? AND version=?`; conflicto → reintento acotado (segunda línea de defensa) |
| Reintento de webhook (mismo `wamid`) | Índice único de `wa_message_id` + transición idempotente `RECEIVED→PROCESSED` |
| Mezcla entre usuarios A/B | Toda operación keyea por `conversation_id`/`wa_id`; índice único parcial de conversación activa por `wa_id` |
| Pérdida de respuestas | Patrón Outbox: la respuesta se persiste en la misma transacción que el procesamiento |
| Envío duplicado por dos workers | `outbox_message.message_id` ÚNICO + reclamo `FOR UPDATE SKIP LOCKED` con transición `PENDING→SENDING` |
| Crash a mitad de envío | Lease: `SENDING` con `lease_expires_at` vencido → el poller la devuelve a `PENDING` y reintenta |
| Reconstrucción de conversación | `conversation_selection` + `message` (+ `outbox_message`) reproducen el historial íntegro |

### 3.3 Transacciones R2DBC reales (el lock se mantiene solo en transacción)

- La Fase A completa (SELECT FOR UPDATE → dedupe → engine → UPDATE estado → selecciones → INSERT outbound + outbox) se ejecuta como **una única transacción real** con `TransactionalOperator` (o `DatabaseClient.inTransaction`): todas las sentencias usan la MISMA conexión y el `FOR UPDATE` mantiene el lock sobre la fila de la conversación desde el SELECT hasta el COMMIT.
- **NO** ejecutar el `SELECT ... FOR UPDATE` en operaciones sueltas sin transacción: con R2DBC cada operación independiente usaría conexiones distintas y el lock no se sostendría (sería un no-op).
- El flujo reactivo debe completarse (commit) dentro de esa transacción: sin `block()` intermedio ni subscripciones separadas. El COMMIT libera la fila para el siguiente mensaje del mismo usuario.
- El `OutboxPoller` usa su propia transacción con `FOR UPDATE SKIP LOCKED` para no bloquearse entre ticks y nunca reclamar la misma fila dos veces.

## 4. Migraciones

- **Flyway** con scripts versionados en `backend/src/main/resources/db/migration/` (`V1__init.sql` crea `conversation`, `conversation_selection`, `message` y `outbox_message`; `V2__seed_menus.sql` si aplica).
- El DDL canónico (CREATE TABLE/INDEXES/CHECK) se autorá en **Fase 3**; este documento es la especificación.
- PostgreSQL 16 (imagen `postgres:16-alpine`) — versión alineada con el entorno local ya disponible.

## 5. Auditoría y privacidad

- Timestamps en todas las tablas (`created_at`, `updated_at`).
- Los datos personales (número de documento) se almacenan solo como parte de la conversación de negocio.
- La auditoría inicial se compone de las 4 tablas: `conversation` + `conversation_selection` + `message` (entrantes y salientes) + `outbox_message` (intentos de entrega). El payload crudo de Meta (`webhook_event`) queda como evolución futura.
- Consideración RGPD/Ley 1581 (Colombia): documentar retención y posibilidad de borrado; el modelo permite eliminar por `conversation_id`.
- Nunca loguear contenido de mensajes, números de documento ni el `payload` del outbox en logs.