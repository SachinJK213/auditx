package com.auditx.report.repository;

import com.auditx.report.model.AuditEventDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

import java.time.Instant;

public interface AuditEventRepository extends ReactiveMongoRepository<AuditEventDocument, String> {

    Flux<AuditEventDocument> findByTenantIdAndTimestampBetween(
            String tenantId, Instant start, Instant end);

    Flux<AuditEventDocument> findByTenantIdAndRiskScoreGreaterThanEqualAndTimestampBetween(
            String tenantId, Double minRiskScore, Instant start, Instant end);
}
