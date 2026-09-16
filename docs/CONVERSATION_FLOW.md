# Flujo de Conversación — WhatsApp Call Center Bot

## 1. Principios de navegación

1. **El estado lo decide todo**: el significado de una entrada numérica depende exclusivamente del estado actual. Un "3" en `PURCHASE_MENU` NO es "Quejas o reclamos".
2. **Entradas válidas solo dentro de su estado**: cada estado declara las opciones que acepta.
3. **Transiciones explícitas**: opción → estado destino (+ selección registrada).
4. **Sin atajos**: no existe forma de llegar al estado final sin pasar por los niveles previos (RF-12 y RF-13).
5. **Comandos globales**: ciertos comandos se interpretan en cualquier estado no terminal.

## 2. Comandos globales

| Comando | Efecto | Ejemplo |
|---|---|---|
| `0` / `volver` / `atras` | Retrocede un nivel (estado padre) y descarta selecciones posteriores | Desde `PRODUCT_MENU` → `PURCHASE_MENU` |
| `menu` / `inicio` / `reiniciar` | Vuelve a `MAIN_MENU` y limpia las selecciones | Desde cualquier estado |
| `cancelar` / `salir` | Terminal `CANCELLED`: despedida y cierre de la conversación | Desde cualquier estado |
| `hola` | Sin conversación activa → crea conversación y muestra `MAIN_MENU`. Con conversación en estado terminal (`FINAL`/`CANCELLED`) → inicia una nueva conversación. En cualquier otro estado → re-muestra el menú | Primer mensaje típico |
| Cualquier otra cosa | Depende del estado (re-prompt si es inválida). **En estado terminal NO responde** (silencio hasta un reinicio explícito) | — |

## 3. Menú principal (Nivel 1) y categorías

```
MAIN_MENU
1. Compra de paquetes   → PURCHASE_MENU
2. Recargas             → RECHARGE_MENU
3. Quejas o reclamos    → COMPLAINT_MENU
4. Información personal → PERSONAL_INFO_MENU
5. Soporte técnico      → SUPPORT_MENU
```

### 3.1 Compra de paquetes (Niveles 2 y 3)

```
PURCHASE_MENU
1. Internet móvil     → PRODUCT_MENU(INTERNET)
2. Minutos ilimitados → PRODUCT_MENU(MINUTOS)
3. Redes sociales     → PRODUCT_MENU(SOCIAL)
4. Todo incluido      → PRODUCT_MENU(COMBO)
```

Ejemplo `PRODUCT_MENU(INTERNET)` (cada producto tiene su propia lista con 4 opciones):

```
1. 5 GB
2. 10 GB
3. 20 GB
4. Ilimitado
```

Diseño inicial de los demás productos (seed ajustable en Fase 2):
- **Minutos**: 15 / 30 / 60 / 100 min.
- **Redes sociales**: 500 MB / 1 GB / 2 GB / Ilimitado.
- **Todo incluido**: Combo 1 / 2 / 3 / 4.

### 3.2 Recargas

```
RECHARGE_MENU
1. Recarga a mi línea    → montos: $5.000 / $10.000 / $20.000 / $50.000
2. Recarga a otro número → ingresar número → montos → identificación
3. Comprar con puntos    → puntos: $2.000 / $5.000 / $10.000 / consultar puntos
4. Recarga internacional → país: Ecuador / Venezuela / Perú / Otro
```

### 3.3 Quejas o reclamos

```
COMPLAINT_MENU
1. Facturación
2. Cobertura
3. Velocidad / calidad
4. Equipos / portabilidad
→ detalle del caso → identificación → confirmación → respuesta final
```

### 3.4 Información personal

```
PERSONAL_INFO_MENU
1. Consultar saldo
2. Consultar plan
3. Cambiar plan
4. Actualizar datos personales
→ submenú de detalle → identificación → confirmación → respuesta final
```

### 3.5 Soporte técnico

```
SUPPORT_MENU
1. Configuración internet (APN)
2. Config. llamadas / mensajes
3. Problemas de señal
4. Guías y tutoriales
→ submenú de detalle → identificación → confirmación → respuesta final

## 4. Modelo de estados

### Definición oficial de los 5 niveles (sin ambigüedad)

| Nivel | Fase | Estados que lo componen |
|---|---|---|
| Nivel 1 | Menú principal | `MAIN_MENU` |
| Nivel 2 | Servicio / categoría | Estados de categoría (Compra, Recargas, Quejas, Info personal, Soporte) |
| Nivel 3 | Producto / subservicio | `PRODUCT_MENU(<contexto>)` / submenús de detalle |
| Nivel 4 | Identificación | `NAME_INPUT` (nombre completo) **+** `IDENTIFICATION_MENU` (tipo de documento) **+** `DOCUMENT_INPUT` (número) — los TRES pertenecen al nivel 4 y se recorren en ese orden |
| Nivel 5 | Confirmación | `CONFIRMATION_MENU` |
| Después de N5 | Respuesta final | `FINAL` (solo alcanzable DESDE el nivel 5) |

> **La identificación tiene TRES pasos: Nombre → Tipo de identificación → Número de documento.** La respuesta final (`FINAL`) solo puede emitirse DESPUÉS de completar el nivel 5. Los niveles 1, 2 y 3 NO pueden saltarse: el grafo de estados no tiene transiciones entre niveles no contiguos.

| Estado | Nivel | Entradas válidas | Transición | Datos que necesita |
|---|---|---|---|---|
| `MAIN_MENU` | 1 | 1-5, comandos globales | 1→PURCHASE_MENU, 2→RECHARGE_MENU, 3→COMPLAINT_MENU, 4→PERSONAL_INFO_MENU, 5→SUPPORT_MENU | — |
| Categorías (PURCHASE_MENU, RECHARGE_MENU, COMPLAINT_MENU, PERSONAL_INFO_MENU, SUPPORT_MENU) | 2 | 1-4, comandos globales | → submenú de nivel 3 correspondiente | Selección nivel 2 |
| `PRODUCT_MENU(<contexto>)` / submenús de detalle | 3 | 1-4, comandos globales | → identificación (o flujo específico, ej. "otro número") | Selección nivel 3 |
| `NAME_INPUT` | 4 | texto libre (nombre completo) | Válido → `IDENTIFICATION_MENU`; inválido → re-prompt | Nombre completo (texto libre) |
| `IDENTIFICATION_MENU` | 4 | 1-4 (C.C., Pasaporte, NIT, Cliente nuevo) | → `DOCUMENT_INPUT` | Selección nivel 4 (tipo de documento) |
| `DOCUMENT_INPUT` | 4 | texto libre (dígitos) | Válido → `CONFIRMATION_MENU`; inválido → re-prompt | Número de documento (texto libre) |
| `CONFIRMATION_MENU` | 5 | 1 Confirmar / 2 Cambiar / 3 Agente / 4 Cancelar | 1→FINAL, 2→nivel 3 de la rama, 3→FINAL(agente), 4→CANCELLED | Selecciones de niveles 1-4 |
| `FINAL` | terminal | cualquier cosa → si es `hola`/`menu`, inicia nueva conversación; en otro caso **no responde** | — | — |
| `CANCELLED` | terminal | cualquier cosa → si es `hola`/`menu`, inicia nueva conversación; en otro caso **no responde** | — | — |

### Silencio en estados terminales

Cuando la conversación alcanza `FINAL` o `CANCELLED` pasa a `status = CLOSED` y el bot deja de emitir mensajes para cualquier entrada que no sea un reinicio explícito (`hola` / `menu`): el mensaje entrante se persiste para auditoría/deduplicación, pero **no se crea mensaje saliente ni fila de outbox**. Esto evita el bucle de «menú principal repetido» que disparaban los mensajes tardíos o reintentos tras la despedida.

### Comportamiento por caso

- **Entrada inválida** (número fuera de rango, texto donde se espera número): mensaje de re-prompt con el menú actual; el estado no cambia. Ej.: `"Opción no válida. Selecciona una opción del 1 al 4:"` + re-lista de opciones.
- **Intento de saltarse un nivel**: imposible por construcción. Cada entrada se interpreta SOLO en el estado actual; no existe ruta que conecte el nivel 1 con el nivel 5.
- **Documento inválido**: según el tipo seleccionado (ver `DocumentValidator`): C.C. 5-10 dígitos, NIT 8-12 dígitos, Pasaporte 5-10 alfanuméricos. Re-prompt sin perder las selecciones previas.

## 5. Ejemplo completo (Compra de paquetes)

| # | Usuario | Bot | Estado resultante |
|---|---|---|---|
| 1 | `hola` | Bienvenida + menú principal (5 opciones) | `MAIN_MENU` |
| 2 | `1` | Compra de paquetes (4 opciones) | `PURCHASE_MENU` |
| 3 | `1` | Internet móvil → paquetes 5/10/20 GB, Ilimitado | `PRODUCT_MENU(INTERNET)` |
| 4 | `2` | «10 GB ✅ Para continuar necesitamos identificarte» + petición de nombre | `NAME_INPUT` |
| 5 | `Juan Pérez` | «¡Gracias, Juan Pérez!» + tipo de identificación (4 opciones) | `IDENTIFICATION_MENU` |
| 6 | `1` | «Cédula de ciudadanía: escribe tu número de documento» | `DOCUMENT_INPUT` |
| 7 | `1234567890` | Confirmación: resumen contextual (👤 Juan Pérez (CC ******7890), 📦 Paquete: 10 GB) + 4 acciones | `CONFIRMATION_MENU` |
| 8 | `1` | **Respuesta final específica** («✅ Tu compra se procesó exitosamente») | `FINAL` |
| 9 | `gracias` | *(silencio: el estado es terminal)* | `FINAL` |
| 10 | `hola` | Nueva conversación → bienvenida + menú principal | `MAIN_MENU` |

La respuesta final NO puede ocurrir antes del paso 8 (RF-13): el estado `FINAL` solo es alcanzable desde `CONFIRMATION_MENU`, y ese estado requiere selecciones de los niveles 1-4 (incluido el nombre).

## 6. Matriz de manejo de entradas

| Entrada | Sin conversación | En MAIN_MENU | En niveles 2-5 | En estado terminal |
|---|---|---|---|---|
| `hola` | Crea conversación → menú principal | Re-muestra menú | Re-muestra menú (no reinicia) | Nueva conversación → menú principal |
| Número válido | Se interpreta en `MAIN_MENU` | Transición del menú | Transición / acción del estado | *Sin respuesta* |
| Número inválido | — | Re-prompt | Re-prompt (se mantiene el estado) | *Sin respuesta* |
| Texto inesperado | Se interpreta en `MAIN_MENU` | Re-prompt | Re-prompt con sugerencias | *Sin respuesta* |
| `menu` / `inicio` | Muestra menú | Menú | Reinicia a `MAIN_MENU` | Nueva conversación → menú principal |
| `volver` / `atras` / `0` | — | «Ya estás en el menú principal» | Retrocede un nivel | *Sin respuesta* |
| `cancelar` / `salir` | — | Cierra con despedida (`CANCELLED`) | Cierra con despedida (`CANCELLED`) | *Sin respuesta* |
| Mensaje vacío | Se ignora (sin respuesta) | Se ignora | Se ignora | Se ignora |
```