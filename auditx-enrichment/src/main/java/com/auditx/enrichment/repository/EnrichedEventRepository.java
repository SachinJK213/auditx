package com.auditx.enrichment.repository;

import com.auditx.enrichment.model.EnrichedEventDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

import java.time.Instant;

public interface EnrichedEventRepository extends ReactiveMongoRepository<EnrichedEventDocument, String> {
    Flux<EnrichedEventDocument> findByTenantIdAndEnrichedAtAfter(String tenantId, Instant after);
}
