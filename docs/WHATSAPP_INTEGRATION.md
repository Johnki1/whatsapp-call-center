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
| `WHATSAPP_API_VERSION` | Versión de Graph API (default `v25.0`) | No |
| `WHATSAPP_GRAPH_BASE_URL` | Host de la Graph API (default `https://graph.facebook.com`) | No |
| `WHATSAPP_API_TIMEOUT_MS` | Timeout de respuesta HTTP en ms (default `10000`) | No |
| `WHATSAPP_CONNECT_TIMEOUT_MS` | Timeout de conexión HTTP en ms (default `5000`) | No |
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

Selección del adaptador mediante `@ConditionalOnProperty` sobre `whatsapp.client.mode`:

| `whatsapp.client.mode` | Adaptador activo | Perfiles típicos |
|---|---|---|
| `mock` (default) | `MockWhatsAppClient` | `local`, `test` |
| `meta` | `MetaWhatsAppClient` | `meta` |

El contrato del puerto es el mismo para ambos adaptadores y **no cambia**:

```java
Mono<WhatsAppSendResult> sendMessage(String waId, String text);
```

**MetaWhatsAppClient** (implementación real — Fase 7A):

- **Endpoint**: `POST {WHATSAPP_GRAPH_BASE_URL}/{WHATSAPP_API_VERSION}/{WHATSAPP_PHONE_NUMBER_ID}/messages`
- **Autenticación**: header `Authorization: Bearer {WHATSAPP_ACCESS_TOKEN}`
- **Content-Type**: `application/json`
- **Body**:

```json
{
  "messaging_product": "whatsapp",
  "to": "<waId>",
  "type": "text",
  "text": { "body": "<texto>" }
}
```

- **Respuesta 2xx**:

```json
{ "messaging_product": "whatsapp", "contacts": [ ... ], "messages": [ { "id": "wamid..." } ] }
```

  El `id` de `messages[0]` se convierte en `new WhatsAppSendResult(wamid)`.

- **Cliente HTTP**: `WebClient` (Spring WebFlux) sobre Reactor Netty. No se usa `RestTemplate`, OkHttp ni otra librería HTTP, y no hay `block()` en el flujo productivo.

#### Un 2xx sin `messages[0].id` es un error

Nunca se devuelve `Mono.empty()`: si el `OutboxPoller` recibiese un `Mono` vacío en lugar de un resultado, no marcaría el mensaje como `SENT` y quedaría colgado en `SENDING` hasta expirar el lease. Un 2xx sin `messages[0].id` produce `Mono.error`.

#### Timeouts

| Propiedad | Variable de entorno | Default | Efecto |
|---|---|---|---|
| `whatsapp.api.timeout-ms` | `WHATSAPP_API_TIMEOUT_MS` | `10000` | `responseTimeout` de Reactor Netty |
| `whatsapp.api.connect-timeout-ms` | `WHATSAPP_CONNECT_TIMEOUT_MS` | `5000` | `CONNECT_TIMEOUT_MILLIS` |

Se aplican en el `HttpClient` de Reactor Netty del adaptador, de modo que una petición contra Meta **nunca puede quedar esperando indefinidamente**. No se añade un `timeout()` reactivo adicional: ambos ya acotan conexión + respuesta.

#### Manejo de errores

Toda respuesta no-2xx (400, 401, 403, 404, 409, 429, 500, 502, 503, 504...) produce `Mono.error(MetaDeliveryException)` con un mensaje **seguro y truncado a 255 caracteres** (igual que `outbox_message.last_error`):

```
Meta API error: status=400, code=131026, message=Message undeliverable., fbtrace_id=AbCdEfTrace
```

Solo se extraen `error.code`, `error.message` y `error.fbtrace_id` del JSON de Meta. **Nunca** se propagan el cuerpo completo de la respuesta, cabeceras ni el `Authorization`.

#### Reintentos: responsabilidad exclusiva del Outbox

`MetaWhatsAppClient` **NO** implementa `retryWhen`, backoff ni ninguna política de reintentos. Solo propaga el error; la política vive en el `OutboxPoller` (Fase 6):

```
MetaWhatsAppClient → Mono.error(...)
        ↓
OutboxPoller → attempts < maxAttempts ? scheduleRetry(backoff exponencial) : markFailed
```

Duplicar la política (reintentos HTTP internos × reintentos del Outbox) multiplicaría las llamadas a Meta y dificultaría la observabilidad. **Un único dueño de la política de reintentos: el Outbox.**

#### Fail fast de configuración

Con `whatsapp.client.mode=meta` se valida al construir el bean que `accessToken`, `phoneNumberId`, `apiVersion` y `baseUrl` no sean nulos ni vacíos. Si falta alguno, el arranque falla con un error claro que **solo menciona el nombre de la variable**:

```
WHATSAPP_ACCESS_TOKEN is required when whatsapp.client.mode=meta
```

El valor del token jamás aparece en el mensaje de la excepción ni en los logs.

#### Seguridad

Nunca se registran: `accessToken`, header `Authorization`, `App Secret` ni `Verify Token`; tampoco el cuerpo completo de las respuestas de error. Sí se registra la configuración no sensible (`baseUrl`, `apiVersion`, `phoneNumberId`, timeouts). Los secretos **nunca** deben ir a Git: se inyectan por variables de entorno (ver `.env.example`).

#### Tests

Los tests de `MetaWhatsAppClient` **no llaman a Meta real**: usan un stub HTTP local sobre Reactor Netty (`StubMetaGraphServer`) en un puerto efímero, sin Internet, sin credenciales reales y sin dependencias nuevas (ni WireMock ni MockWebServer).

**MockWhatsAppClient**:
- En perfil `local`/`test`: registra en log y devuelve un `wamid` simulado. Permite el E2E completo sin Meta. **Se conserva** como adaptador por defecto.

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

## 9. Activar el envío real con Meta

Requisitos de configuración (Fase 7A). **Ningún cambio de código es necesario:**

1. Crear la app en Meta for Developers y obtener el `WHATSAPP_APP_SECRET`.
2. Configurar WABA + número de teléfono y obtener `WHATSAPP_PHONE_NUMBER_ID` (y `WHATSAPP_WABA_ID`).
3. Generar un token de acceso (System User con permiso `whatsapp_business_messaging`) → `WHATSAPP_ACCESS_TOKEN`.
4. Definir las variables restantes: `WHATSAPP_API_VERSION`, `WHATSAPP_GRAPH_BASE_URL`, `WHATSAPP_API_TIMEOUT_MS`, `WHATSAPP_CONNECT_TIMEOUT_MS`.
5. Arrancar con `SPRING_PROFILES_ACTIVE=meta` y `BOT_WHATSAPP_CLIENT=meta`.

Si falta `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_API_VERSION` o `WHATSAPP_GRAPH_BASE_URL`, el arranque **falla con un mensaje claro** (fail fast) en lugar de fallar silenciosamente en cada envío.

Verificación sin tocar Meta: `WHATSAPP_GRAPH_BASE_URL` es configurable, de modo que se puede apuntar a un servidor local que emule la Graph API. Los tests automatizados ya lo hacen con `StubMetaGraphServer`.

> La exposición pública del webhook y el despliegue (HTTPS, DNS, infraestructura) corresponden a una fase posterior y quedan **fuera del alcance de la Fase 7A**.

## 10. Límites a conocer

- Ventana de 24h: solo podemos iniciar mensajes a usuarios que nos hayan escrito (no aplica a este bot, que siempre responde dentro de la conversación iniciada por el usuario).
- Límites de throughput de la API (tiers de mensajes): fuera de alcance en esta fase.
- `wamid` es la clave correcta de deduplicación (única por mensaje).
- El cuerpo de un mensaje de texto tiene un límite de 4096 caracteres en la Graph API; las respuestas del motor conversacional están muy por debajo de ese límite.
- La entrega externa es **at-least-once**: ver §7 (ventana de crash tras el POST y recuperación por lease).