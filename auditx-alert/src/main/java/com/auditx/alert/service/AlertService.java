package com.auditx.alert.service;

import com.auditx.alert.model.AlertDocument;
import com.auditx.alert.repository.AlertRepository;
import com.auditx.alert.repository.TenantRepository;
import com.auditx.common.dto.AlertDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository alertRepository;
    private final TenantRepository tenantRepository;
    private final NotificationDispatcher notificationDispatcher;

    public AlertService(AlertRepository alertRepository,
                        TenantRepository tenantRepository,
                        NotificationDispatcher notificationDispatcher) {
        this.alertRepository = alertRepository;
        this.tenantRepository = tenantRepository;
        this.notificationDispatcher = notificationDispatcher;
    }

    public Mono<Void> process(AlertDto alert) {
        // Idempotency: skip if alertId already exists
        return alertRepository.findByAlertId(alert.alertId())
                .flatMap(existing -> {
                    log.debug("Duplicate alertId={}, skipping", alert.alertId());
                    return Mono.<Void>empty();
                })
                .switchIfEmpty(Mono.defer(() -> persistAndDispatch(alert)));
    }

    private Mono<Void> persistAndDispatch(AlertDto alert) {
        AlertDocument doc = new AlertDocument();
        doc.setAlertId(alert.alertId());
        doc.setTenantId(alert.tenantId());
        doc.setEventId(alert.eventId());
        doc.setUserId(alert.userId());
        doc.setRiskScore(alert.riskScore());
        doc.setRuleMatches(alert.ruleMatches());
        doc.setStatus("OPEN");
        doc.setCreatedAt(alert.createdAt() != null ? alert.createdAt() : Instant.now());
        doc.setEscalationLevel(1);

        return alertRepository.save(doc)
                .flatMap(saved ->
                        tenantRepository.findByTenantId(saved.getTenantId())
                                .flatMap(tenant -> notificationDispatcher.dispatch(saved, tenant))
                                .switchIfEmpty(Mono.fromRunnable(() ->
                                        log.warn("No tenant config found for tenantId={}", saved.getTenantId())))
                )
                .then();
    }
}
