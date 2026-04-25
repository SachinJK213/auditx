package com.auditx.report.repository;

import com.auditx.report.model.AlertDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

import java.time.Instant;

public interface AlertRepository extends ReactiveMongoRepository<AlertDocument, String> {

    Flux<AlertDocument> findByTenantIdAndCreatedAtBetween(
            String tenantId, Instant start, Instant end);
}
