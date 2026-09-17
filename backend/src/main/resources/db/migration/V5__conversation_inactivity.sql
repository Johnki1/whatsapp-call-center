-- Las conversaciones anteriores mantienen el contexto, sin activar recordatorios retroactivos.
ALTER TABLE conversation
    ADD COLUMN last_interaction_at TIMESTAMPTZ,
    ADD COLUMN last_inbound_at TIMESTAMPTZ,
    ADD COLUMN last_bot_message_at TIMESTAMPTZ,
    ADD COLUMN reminder_at TIMESTAMPTZ,
    ADD COLUMN reengagement_pending BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN last_prompt_payload TEXT,
    ADD COLUMN awaiting_reply_message_id UUID;

UPDATE conversation SET last_interaction_at = updated_at;
ALTER TABLE conversation ALTER COLUMN last_interaction_at SET NOT NULL;

CREATE INDEX idx_conversation_reminder_due ON conversation (last_bot_message_at)
    WHERE status = 'ACTIVE' AND state NOT IN ('FINAL', 'CANCELLED', 'HUMAN_AGENT')
      AND reminder_at IS NULL AND NOT reengagement_pending;
