-- ============================================================
-- BotWap — V3: nuevo estado NAME_INPUT (primer paso del Nivel 4)
-- Contexto: el flujo de identificación pasa a ser
--   Nombre -> Tipo de identificación -> Número de documento.
-- El nombre se captura en el estado NAME_INPUT (nivel 4) antes de
-- IDENTIFICATION_MENU y se persiste como selección
-- (conversation_selection: level=4, state_key='NAME_INPUT').
-- ============================================================

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
        'FINAL', 'CANCELLED'
    ));
