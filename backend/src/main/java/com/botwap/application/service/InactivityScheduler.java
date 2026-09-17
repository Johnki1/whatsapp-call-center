package com.botwap.application.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnProperty(prefix = "app.inactivity", name = "enabled", havingValue = "true")
public class InactivityScheduler {
    private final InactivityService service;

    public InactivityScheduler(InactivityService service) {
        this.service = service;
    }

    /** Spring espera la terminación del publisher antes del siguiente fixedDelay. */
    @Scheduled(fixedDelayString = "${app.inactivity.poll-interval-ms:1000}")
    public Mono<Void> poll() {
        return Mono.defer(service::processDue);
    }
}
