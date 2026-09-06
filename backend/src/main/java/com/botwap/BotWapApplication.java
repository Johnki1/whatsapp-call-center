package com.botwap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada del backend de BotWap (WhatsApp Call Center Bot).
 *
 * <p>Aplicación reactiva: Spring WebFlux + R2DBC. Las migraciones de esquema
 * se ejecutan con Flyway (driver JDBC solo para migraciones).</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BotWapApplication {

    public static void main(String[] args) {
        SpringApplication.run(BotWapApplication.class, args);
    }
}