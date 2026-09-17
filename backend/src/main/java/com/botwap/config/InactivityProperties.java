package com.botwap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.inactivity")
public record InactivityProperties(@DefaultValue("30m") Duration reengagementDelay) {
    public InactivityProperties {
        if (reengagementDelay == null || reengagementDelay.isNegative() || reengagementDelay.isZero()) {
            throw new IllegalArgumentException("app.inactivity.reengagement-delay debe ser positivo");
        }
    }
}
