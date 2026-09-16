package com.botwap.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Habilita las propiedades de configuración del asistente de IA (Gemini).
 */
@Configuration
@EnableConfigurationProperties(GeminiProperties.class)
public class GeminiConfig {
}
