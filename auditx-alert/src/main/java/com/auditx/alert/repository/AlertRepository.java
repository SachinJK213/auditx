package com.auditx.alert.repository;

import com.auditx.alert.model.AlertDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface AlertRepository extends ReactiveMongoRepository<AlertDocument, String> {
    Mono<AlertDocument> findByAlertId(String alertId);
    Mono<AlertDocument> findByTenantIdAndAlertId(String tenantId, String alertId);
    // Used by EscalationService to find alerts overdue for escalation
    Flux<AlertDocument> findAllByStatusAndEscalationLevelAndCreatedAtBefore(
            String status, int escalationLevel, Instant cutoff);
    Flux<AlertDocument> findAllByStatusAndEscalationLevelAndEscalatedAtBefore(
            String status, int escalationLevel, Instant cutoff);
}
