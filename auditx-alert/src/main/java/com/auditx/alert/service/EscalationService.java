package com.auditx.alert.service;

import com.auditx.alert.repository.AlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class EscalationService {

    private static final Logger log = LoggerFactory.getLogger(EscalationService.class);

    private final AlertRepository alertRepository;

    @Value("${auditx.escalation.l1-timeout-minutes:15}")
    private int l1TimeoutMinutes;

    @Value("${auditx.escalation.l2-timeout-minutes:30}")
    private int l2TimeoutMinutes;

    public EscalationService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @Scheduled(fixedDelayString = "${auditx.escalation.check-interval-ms:300000}")
    public void checkAndEscalate() {
        escalateL1ToL2().subscribe();
        escalateL2ToL3().subscribe();
    }

    // L1 alerts unacknowledged beyond l1-timeout → promote to L2
    private reactor.core.publisher.Mono<Void> escalateL1ToL2() {
        Instant l1Cutoff = Instant.now().minus(l1TimeoutMinutes, ChronoUnit.MINUTES);
        return alertRepository.findAllByStatusAndEscalationLevelAndCreatedAtBefore("OPEN", 1, l1Cutoff)
                .flatMap(alert -> {
                    alert.setEscalationLevel(2);
                    alert.setEscalatedAt(Instant.now());
                    return alertRepository.save(alert)
                            .doOnSuccess(saved -> log.info(
                                    "{\"alertId\":\"{}\",\"escalatedTo\":2,\"tenantId\":\"{}\"}",
                                    saved.getAlertId(), saved.getTenantId()));
                })
                .then();
    }

    // L2 alerts unacknowledged beyond l2-timeout → promote to L3
    private reactor.core.publisher.Mono<Void> escalateL2ToL3() {
        Instant l2Cutoff = Instant.now().minus(l2TimeoutMinutes, ChronoUnit.MINUTES);
        return alertRepository.findAllByStatusAndEscalationLevelAndEscalatedAtBefore("OPEN", 2, l2Cutoff)
                .flatMap(alert -> {
                    alert.setEscalationLevel(3);
                    alert.setEscalatedAt(Instant.now());
                    return alertRepository.save(alert)
                            .doOnSuccess(saved -> log.warn(
                                    "{\"alertId\":\"{}\",\"escalatedTo\":3,\"tenantId\":\"{}\",\"warn\":\"L3 escalation reached\"}",
                                    saved.getAlertId(), saved.getTenantId()));
                })
                .then();
    }
}
