package com.botwap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test base de la FASE 2: confirma que el contexto de Spring Boot inicia
 * correctamente (con PostgreSQL real vía Testcontainers y Flyway ejecutado).
 */
class BotWapApplicationTests extends BaseIntegrationTest {

    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.isRunning()).isTrue();
    }
}