package com.auditx.riskengine.repository;

import com.auditx.riskengine.model.AuditEventDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface AuditEventRepository extends ReactiveMongoRepository<AuditEventDocument, String> {
    Mono<AuditEventDocument> findByEventId(String eventId);

    Flux<AuditEventDocument> findByTenantIdAndUserIdAndOutcomeAndTimestampAfter(
            String tenantId, String userId, String outcome, Instant after);

    Flux<AuditEventDocument> findByTenantIdAndUserIdAndTimestampAfter(
            String tenantId, String userId, Instant after);
}
