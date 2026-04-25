package com.auditx.parser.repository;

import com.auditx.parser.model.AuditEventDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface AuditEventRepository extends ReactiveMongoRepository<AuditEventDocument, String> {
    Mono<AuditEventDocument> findByEventId(String eventId);
}
