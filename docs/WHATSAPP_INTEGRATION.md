# Integración con WhatsApp Cloud API (Meta)

## 1. Visión general

La integración se resuelve en dos frentes:

1. **Recepción**: webhook `GET` (verificación) y `POST` (eventos).
2. **Envío**: `WhatsAppClient` (port) con dos adaptadores: `MockWhatsAppClient` (dev/test) y `MetaWhatsAppClient` (producción).

El motor conversacional **desconoce** el canal: solo produce texto de respuesta. La conexión real con Meta se activa mediante configuración (perfil `meta` + variables de entorno), sin tocar el núcleo.

## 2. Variables de entorno requeridas

| Variable | Descripción | ¿Secreta? |
|---|---|---|
| `WHATSAPP_VERIFY_TOKEN` | Token de verificación del webhook (elegido por nosotros) | Sí |
| `WHATSAPP_APP_SECRET` | App Secret de la app de Meta | Sí |
| `WHATSAPP_ACCESS_TOKEN` | Token de acceso (System User / WABA token) | Sí |
| `WHATSAPP_PHONE_NUMBER_ID` | ID del número de teléfono | No |
| `WHATSAPP_WABA_ID` | ID de la cuenta WABA | No |
| `WHATSAPP_API_VERSION` | Versión de Graph API (ej. `v21.0`) | No |
| `DB_*` | Credenciales de la BD | Sí (password) |
| `BOTWAP_*` | Config genérica del bot | No |

Solo se documentan en `.env.example`; NUNCA en Git con valores reales.

## 3. Verificación del webhook (GET)

Meta invoca:

```
GET /webhook/whatsapp?hub.mode=subscribe&hub.verify_token=<X>&hub.challenge=<Y>
```

Regla:
- `hub.mode == "subscribe"` y `hub.verify_token == WHATSAPP_VERIFY_TOKEN` → responder `200` con body plano = `hub.challenge`.
- Cualquier otra cosa → `403`.

## 4. Recepción (POST) y extracción

El payload de Meta tiene la estructura:

```
{
  "object": "whatsapp_business_account",
  "entry": [{
    "id": "<WABA_ID>",
    "changes": [{
      "field": "messages",
      "value": {
        "messaging_product": "whatsapp",
        "metadata": {"display_phone_number": "...", "phone_number_id": "..."},
        "contacts": [{"wa_id": "573001234567", "profile": {"name": "..."}}],
        "messages": [{"from": "573001234567", "id": "wamid.XXX", "type": "text",
                      "text": {"body": "hola"}}],
        "statuses": [...]   // ignorar para este bot
      }
    }]
  }]
}
```

Reglas de extracción/filtrado:
- Solo `field == "messages"`.
- Solo mensajes con `type == "text"` (las interacciones con botones llegarían como `interactive`; fuera de alcance en la primera iteración).
- Ignorar `statuses` (entregas, lecturas) y mensajes procedentes de nuestro propio número.
- Extraer: `wa_id` (de `from` o `contacts[].wa_id`), `wamid` (de `id`), body (de `text.body`).

## 5. Verificación de firma (seguridad)

Todo POST debe autenticarse con el header `X-Hub-Signature-256`:

```
sha256=<HMAC-SHA256(clave=WHATSAPP_APP_SECRET, mensaje=raw body del request)>
```

- Se compara con `MessageDigest.isEqual(...)` (tiempo constante).
- Fallo → `400` + log de advertencia (probable ataque o configuración errónea).
- Se necesita el **body crudo**: se verifica antes de deserializar (WebFlux: lectura del `DataBuffer` / `ServerRequest`).

## 6. Envío de mensajes — WhatsAppClient (port)

```
WhatsAppClient (domain port)
 ├── MockWhatsAppClient     → dev/test: persiste/registra, responde éxito simulado
 └── MetaWhatsAppClient     → prod: POST a Graph API
```

Selección por perfil Spring (`@ConditionalOnProperty`, ej. `bot.whatsapp.client=mock|meta`).

**MetaWhatsAppClient**:
- Endpoint: `POST https://graph.facebook.com/{WHATSAPP_API_VERSION}/{WHATSAPP_PHONE_NUMBER_ID}/messages`
- Header: `Authorization: Bearer {WHATSAPP_ACCESS_TOKEN}`
- Body: `{ "messaging_product": "whatsapp", "to": "<wa_id>", "type": "text", "text": {"body": "<texto>"} }`
- Cliente: `WebClient` reactivo con `timeout` (ej. 10s) y política de reintentos (`retryWhen` con backoff) solo para errores transitorios (429/5xx).
- Errores 401 → configuración inválida; 400 → payload inválido (se logea a nivel interno, sin datos sensibles).

**MockWhatsAppClient**:
- En perfil `local`/`test`: registra en log estructurado y opcionalmente en BD (mensaje outbound `SENT`). Permite E2E completo sin Meta.

## 7. Fiabilidad y consistencia de la entrega (Outbox)

| Aspecto | Diseño |
|---|---|
| Qué ocurre ANTES del 200 | Firma → extracción → UNA transacción R2DBC: dedupe (`RECEIVED→PROCESSED`), procesamiento del engine, UPDATE de estado/selecciones, INSERT del mensaje saliente `PENDING` e INSERT de `outbox_message` `PENDING` → COMMIT |
| Qué significa el 200 | "Recibido, validado, procesado y respuesta durable en el Outbox". NO significa "entregado al usuario" |
| Qué ocurre DESPUÉS del 200 | El `OutboxPoller` (mismo proceso, sin broker) reclama la fila con `FOR UPDATE SKIP LOCKED`, la marca `SENDING` (lease 60 s), envía por `WhatsAppClient` y la confirma `SENT` |
| Fallo de procesamiento (antes del commit) | Rollback total: el entrante sigue `RECEIVED`; el reintento de Meta re-procesa limpio (el dedupe solo actúa contra mensajes ya `PROCESSED`) |
| Reintentos de entrega | Los gestiona el Outbox (backoff exponencial hasta N intentos), NO el reintento del webhook. Si la app cae, las filas `PENDING` persisten y el poller las retoma al arrancar |
| Crash a mitad de envío | Lease: `SENDING` con `lease_expires_at` vencido se devuelve a `PENDING` y se reintenta (entrega *at-least-once*; posible duplicado extremadamente raro solo si el crash ocurre tras el POST a Meta) |
| Mensajes simultáneos | `SELECT ... FOR UPDATE` de la conversación dentro de la transacción de la Fase A (ver DATABASE_DESIGN §3) |
| Sin `webhook_event` | La auditoría inicial se cubre con `message` + `conversation` + `conversation_selection` + `outbox_message`; el payload crudo quedará como evolución futura |

## 8. Desarrollo local sin Meta

1. `docker compose up -d` (PostgreSQL).
2. `mvn spring-boot:run -Dspring-boot.run.profiles=local` → usa `MockWhatsAppClient`.
3. Smoke test: POST un payload simulado al webhook. (Se proveerá un script de ejemplo en Fase 4.)
4. El `OutboxPoller` del perfil local usa el mock: la respuesta también pasa por la cola (misma Fase B), solo que el adaptador es simulado. El flujo E2E es idéntico al de producción → los tests de integración cubren exactamente el mismo camino, outbox incluido.

## 9. Conectar Meta real (cuando el registro esté completo)

1. Crear app en Meta for Developers, obtener `WHATSAPP_APP_SECRET`.
2. Configurar WABA + phone number; obtener `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_WABA_ID`.
3. Generar token de acceso (System User con permisos `whatsapp_business_messaging`).
4. Exponer el backend con URL pública (ngrok) y configurar el webhook en el dashboard de Meta apuntando a `https://<túnel>/webhook/whatsapp`, con el mismo `verify_token`.
5. Suscribirse al campo `messages`.
6. Poner las variables de entorno reales y arrancar con perfil `meta`.
   **Ningún cambio de código es necesario.**

## 10. Límites a conocer

- Ventana de 24h: solo podemos iniciar mensajes a usuarios que nos hayan escrito (no aplica a este bot, que siempre responde dentro de la conversación iniciada por el usuario).
- Límites de throughput de la API (tiers de mensajes): fuera de alcance en esta fase.
- `wamid` es la clave correcta de deduplicación (única por mensaje).