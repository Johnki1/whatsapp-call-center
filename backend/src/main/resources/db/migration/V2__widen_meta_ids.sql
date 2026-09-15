-- ============================================================
-- BotWap — V2: ampliar IDs de Meta (wamid reales > 64 chars)
-- Causa raiz en produccion:
--   PostgresqlBadGrammarException: value too long for type
--   character varying(64) en INSERT INTO message (... wa_message_id ...)
-- Los wamid que envia Meta (p. ej. "wamid.HBgM...") superan los 64
-- caracteres, lo que abortaba el INSERT, envenenaba la transaccion
-- R2DBC (ROLLBACK en commit) y devolvia HTTP 500 al webhook.
--
-- Cambios:
--   1. message.wa_message_id: VARCHAR(64) -> VARCHAR(255).
--      255 cubre con holgura los wamid observados y mantiene el
--      indice unico parcial uq_message_wa_message_id eficiente
--      (un TEXT tambien valdria, pero 255 acota y sigue indexable).
--   2. conversation.wa_id / outbox_message.wa_id: VARCHAR(20) -> VARCHAR(32).
--      Preventivo: E.164 son maximo 15 digitos, pero se deja margen para
--      formatos con prefijos o futuros identificadores de Meta sin tener
--      que volver a migrar. No rompe datos existentes (solo ensancha).
-- NOTA: NO se trunca en Java: truncar wamids distintos al mismo prefijo
-- rompería la deduplicación (falsos duplicados). La BD es la red de
-- seguridad y debe aceptar el ID completo.
-- ============================================================

ALTER TABLE message
    ALTER COLUMN wa_message_id TYPE VARCHAR(255);

ALTER TABLE conversation
    ALTER COLUMN wa_id TYPE VARCHAR(32);

ALTER TABLE outbox_message
    ALTER COLUMN wa_id TYPE VARCHAR(32);
