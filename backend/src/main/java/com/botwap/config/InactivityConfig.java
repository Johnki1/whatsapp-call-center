package com.botwap.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(InactivityProperties.class)
public class InactivityConfig {
    @Bean
    public Clock conversationClock() {
        return Clock.systemUTC();
    }
}
