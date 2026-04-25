package com.auditx.llm.repository;

import com.auditx.llm.model.AuditEventDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface AuditEventRepository extends ReactiveMongoRepository<AuditEventDocument, String> {
    Mono<AuditEventDocument> findByEventIdAndTenantId(String eventId, String tenantId);
}
