package com.botwap.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Habilita las propiedades de configuración del OutboxPoller.
 *
 * <p>Registra {@link OutboxProperties} como bean de propiedades configurables
 * mediante el prefijo {@code app.outbox}.</p>
 */
@Configuration
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxConfig {
}
