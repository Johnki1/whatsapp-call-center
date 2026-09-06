# BotWap — WhatsApp Call Center Bot

Bot de atención al cliente mediante WhatsApp construido con la **WhatsApp Cloud API de Meta**, **Spring WebFlux** (reactivo de extremo a extremo) y **PostgreSQL + R2DBC**, con una máquina de estados conversacional y entrega confiable de respuestas mediante patrón **Outbox**.

> **Estado del proyecto**: Fase 2 completada (esqueleto técnico). La lógica conversacional y la integración real con Meta se construyen en las fases siguientes.

## Stack tecnológico

| Capa | Tecnología |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3 (WebFlux) |
| Persistencia | Spring Data R2DBC (PostgreSQL 16) — **sin JPA** |
| Migraciones | Flyway (driver JDBC solo para migraciones) |
| Tests | JUnit 5, WebTestClient, Reactor Test, Testcontainers |
| Contenedores | Docker + Docker Compose |

## Requisitos

- Java 21 (JDK)
- Maven 3.9+
- Docker + Docker Compose (para PostgreSQL y los tests de integración)

## Estructura básica

```
botwap/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/botwap/
│       │   ├── config/           # propiedades de configuración
│       │   ├── domain/           # modelos, enums, puertos y motor conversacional
│       │   ├── application/      # casos de uso (orquestación) y excepciones
│       │   └── infrastructure/   # webhook, clientes WhatsApp (mock/meta), persistencia y outbox
│       ├── main/resources/       # application-{local,test,meta}.yml + migraciones Flyway
│       └── test/java/com/botwap/ # tests base de integración
├── database/                     # notas de referencia del esquema
├── docs/                         # documentación de arquitectura
├── docker-compose.yml
├── .env.example
└── .gitignore
```

## Configuración (variables de entorno)

1. Copia `.env.example` a `.env` y completa los valores locales.
2. **Nunca** subas `.env` a Git (está excluido por `.gitignore`).
3. Los secretos de Meta (`WHATSAPP_*`) pueden dejarse vacíos en desarrollo: el sistema funciona con `MockWhatsAppClient`.

## Cómo ejecutar

### Con Maven (desarrollo)

```bash
# 1. Levantar solo PostgreSQL (mapeado al puerto 5433 del host)
docker compose up -d postgres

# 2. Ejecutar la aplicación con el perfil local (usa el Mock de WhatsApp)
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Verificación rápida: `curl http://localhost:8080/actuator/health`.

### Con Docker Compose (todo el stack)

```bash
docker compose up -d --build
```

- Backend: `http://localhost:8080` (healthcheck en `/actuator/health`)
- PostgreSQL interno: `postgres:5432` (desde el host se accede por `localhost:5433`)

## Cómo ejecutar los tests

```bash
cd backend
mvn test
```

Los tests de integración levantan PostgreSQL con Testcontainers (requiere Docker en ejecución) y aplican la migración Flyway.

> **Nota (daemon Docker ≥ v29)**: docker-java negocia por defecto la API v1.32, que los daemons recientes rechazan (mínima 1.40). El `pom.xml` ya fija `api.version=1.40` en surefire para resolverlo.

## Documentación

La arquitectura aprobada está documentada en `docs/`:

- `ARCHITECTURE.md`
- `CONVERSATION_FLOW.md`
- `DATABASE_DESIGN.md`
- `WHATSAPP_INTEGRATION.md`
- `TESTING_STRATEGY.md`