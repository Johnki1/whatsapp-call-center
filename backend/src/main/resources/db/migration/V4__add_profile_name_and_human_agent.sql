-- ============================================================
-- BotWap — V4: perfil de WhatsApp + handoff humano (Fase V2)
-- Cambios:
--   1. conversation.profile_name: nombre público del perfil de
--      WhatsApp del usuario (webhook: contacts[0].profile.name).
--      Permite saludos personalizados (ej. "¡Hola Jhonki! 👋").
--      Nullable: los registros previos a V4 y los payloads sin
--      contacts se quedan con NULL.
--   2. Estado HUMAN_AGENT: handoff a un asesor humano. En este
--      estado el bot permanece en silencio (no genera respuestas
--      automáticas); la conversación se cierra y solo un reinicio
--      explícito (hola/menu) abre una nueva.
-- ============================================================

ALTER TABLE conversation
    ADD COLUMN IF NOT EXISTS profile_name VARCHAR(100);

ALTER TABLE conversation
    DROP CONSTRAINT IF EXISTS ck_conversation_state;

ALTER TABLE conversation
    ADD CONSTRAINT ck_conversation_state CHECK (state IN (
        'MAIN_MENU',
        'PURCHASE_MENU', 'RECHARGE_MENU', 'COMPLAINT_MENU',
        'PERSONAL_INFO_MENU', 'SUPPORT_MENU',
        'PRODUCT_MENU', 'DETAIL_MENU',
        'NAME_INPUT', 'IDENTIFICATION_MENU', 'DOCUMENT_INPUT',
        'CONFIRMATION_MENU',
        'FINAL', 'CANCELLED', 'HUMAN_AGENT'
    ));
