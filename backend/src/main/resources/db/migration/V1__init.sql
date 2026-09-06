-- ============================================================
-- BotWap — V1: esquema inicial (FASE 2)
-- Fuente de verdad: docs/DATABASE_DESIGN.md (aprobado en FASE 1.5)
-- webhook_event queda FUERA del alcance inicial.
-- ============================================================

-- ------------------------------------------------------------
-- 1. conversation
-- Contexto de estado de un usuario. UNA activa por wa_id.
-- ------------------------------------------------------------
CREATE TABLE conversation (
    id          UUID PRIMARY KEY,
    wa_id       VARCHAR(20)  NOT NULL,
    state       VARCHAR(50)  NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    closed_at   TIMESTAMPTZ,

    CONSTRAINT ck_conversation_status CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT ck_conversation_state CHECK (state IN (
        'MAIN_MENU',
        'PURCHASE_MENU', 'RECHARGE_MENU', 'COMPLAINT_MENU',
        'PERSONAL_INFO_MENU', 'SUPPORT_MENU',
        'PRODUCT_MENU', 'DETAIL_MENU',
        'IDENTIFICATION_MENU', 'DOCUMENT_INPUT',
        'CONFIRMATION_MENU',
        'FINAL', 'CANCELLED'
    ))
);

-- Una sola conversación ACTIVA por número (aislamiento por wa_id).
CREATE UNIQUE INDEX uq_conversation_active_wa_id
    ON conversation (wa_id) WHERE status = 'ACTIVE';

-- Consultas de administración.
CREATE INDEX idx_conversation_status_updated
    ON conversation (status, updated_at);

-- ------------------------------------------------------------
-- 2. conversation_selection
-- Una fila por nivel navegado. Único por (conversation_id, level).
-- ------------------------------------------------------------
CREATE TABLE conversation_selection (
    id              UUID PRIMARY KEY,
    conversation_id UUID         NOT NULL,
    level           SMALLINT     NOT NULL,
    state_key       VARCHAR(50)  NOT NULL,
    option_key      VARCHAR(50)  NOT NULL,
    display_label   VARCHAR(120) NOT NULL,
    metadata        JSONB,
    selected_at     TIMESTAMPTZ  NOT NULL,

    CONSTRAINT fk_selection_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversation (id) ON DELETE CASCADE,
    CONSTRAINT ck_selection_level CHECK (level BETWEEN 1 AND 5)
);

-- Un nivel, una selección: navegar de vuelta hace upsert.
CREATE UNIQUE INDEX uq_selection_conversation_level
    ON conversation_selection (conversation_id, level);

-- Consultas por conversación.
CREATE INDEX idx_selection_conversation
    ON conversation_selection (conversation_id);

-- ------------------------------------------------------------
-- 3. message
-- Registro de mensajes entrantes y salientes (auditoría + dedupe).
-- ------------------------------------------------------------
CREATE TABLE message (
    id              UUID PRIMARY KEY,
    conversation_id UUID         NOT NULL,
    wa_message_id   VARCHAR(64),
    direction       VARCHAR(10)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    type            VARCHAR(20)  NOT NULL,
    content         TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    sent_at         TIMESTAMPTZ,

    CONSTRAINT fk_message_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversation (id) ON DELETE CASCADE,
    CONSTRAINT ck_message_direction CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    CONSTRAINT ck_message_status   CHECK (status IN ('RECEIVED', 'PROCESSED', 'PENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_message_type     CHECK (type IN ('TEXT', 'INTERACTIVE'))
);

-- Deduplicación de reintentos del webhook (único para wamids reales).
CREATE UNIQUE INDEX uq_message_wa_message_id
    ON message (wa_message_id) WHERE wa_message_id IS NOT NULL;

-- Reconstrucción / auditoría cronológica de la conversación.
CREATE INDEX idx_message_conversation_created
    ON message (conversation_id, created_at);

-- ------------------------------------------------------------
-- 4. outbox_message
-- Cola de salida confiable (patrón Outbox). Una fila por respuesta.
-- ------------------------------------------------------------
CREATE TABLE outbox_message (
    id                UUID PRIMARY KEY,
    message_id        UUID          NOT NULL,
    conversation_id   UUID          NOT NULL,
    wa_id             VARCHAR(20)   NOT NULL,
    payload           JSONB         NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    attempts          SMALLINT      NOT NULL DEFAULT 0,
    next_attempt_at   TIMESTAMPTZ   NOT NULL,
    lease_expires_at  TIMESTAMPTZ,
    last_error        VARCHAR(255),
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    sent_at           TIMESTAMPTZ,

    CONSTRAINT fk_outbox_message
        FOREIGN KEY (message_id) REFERENCES message (id),
    CONSTRAINT fk_outbox_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversation (id) ON DELETE CASCADE,
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_outbox_attempts CHECK (attempts >= 0)
);

-- Un outbox por respuesta: imposible duplicar el envío.
CREATE UNIQUE INDEX uq_outbox_message_id
    ON outbox_message (message_id);

-- Índice de cola del OutboxPoller (status + backoff).
CREATE INDEX idx_outbox_queue
    ON outbox_message (status, next_attempt_at);

-- Diagnóstico.
CREATE INDEX idx_outbox_conversation
    ON outbox_message (conversation_id);
CREATE INDEX idx_outbox_wa_id
    ON outbox_message (wa_id);