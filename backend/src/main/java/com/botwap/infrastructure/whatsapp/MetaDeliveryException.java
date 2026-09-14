package com.botwap.infrastructure.whatsapp;

/**
 * Error de entrega devuelto por la WhatsApp Cloud API (Graph API de Meta).
 *
 * <p>El mensaje contiene únicamente información segura y útil para diagnóstico
 * ({@code status}, {@code code}, {@code message}, {@code fbtrace_id}) y está
 * truncado a la longitud de {@code outbox_message.last_error}. <strong>Nunca</strong>
 * incluye el access token, cabeceras de autorización ni el cuerpo completo de la
 * respuesta.</p>
 *
 * <p>El reintento/backoff NO se decide aquí: el {@code OutboxPoller} es el único
 * responsable de la política de reintentos (Fase 6). Este adaptador solo propaga
 * el error para que el Outbox lo registre y decida.</p>
 */
public class MetaDeliveryException extends RuntimeException {

    public MetaDeliveryException(String message) {
        super(message);
    }
}