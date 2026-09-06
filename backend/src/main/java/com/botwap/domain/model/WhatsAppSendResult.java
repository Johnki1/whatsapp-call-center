package com.botwap.domain.model;

/**
 * Resultado de un envío a través de {@link com.botwap.domain.port.WhatsAppClient}.
 *
 * <p>El {@code wamid} es el identificador que devuelve la API de Meta al
 * confirmar la entrega; en el Mock se genera un valor simulado.</p>
 */
public record WhatsAppSendResult(String wamid) {
}