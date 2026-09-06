package com.botwap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base común de los tests de integración: PostgreSQL real vía Testcontainers
 * (misma imagen que docker-compose) y cliente WhatsApp «mock».
 *
 * <p>El {@code @DynamicPropertySource} sobrescribe las URLs de R2DBC y Flyway
 * apuntando al contenedor, de modo que cada suite valida el esquema de la
 * migración V1 de verdad.</p>
 *
 * <p><strong>Contenedor compartido por JVM</strong>: se inicia una sola vez en
 * un bloque estático (patrón "shared container"). No se usa
 * {@code @Container}/{@code @Testcontainers} porque ese extension detiene el
 * contenedor al terminar cada clase de tests, cambiando el puerto mapeado y
 * dejando muertos los pools R2DBC de las siguientes clases.</p>
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = startPostgres();

    private static PostgreSQLContainer<?> startPostgres() {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("botwap_test")
                .withUsername("botwap_test")
                .withPassword("botwap_test");
        container.start();
        return container;
    }

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://%s:%d/%s".formatted(
                POSTGRES.getHost(), POSTGRES.getMappedPort(5432), POSTGRES.getDatabaseName()));
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", () -> "jdbc:postgresql://%s:%d/%s".formatted(
                POSTGRES.getHost(), POSTGRES.getMappedPort(5432), POSTGRES.getDatabaseName()));
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired
    protected WebTestClient webTestClient;

    @Autowired
    protected org.springframework.context.ConfigurableApplicationContext applicationContext;
}