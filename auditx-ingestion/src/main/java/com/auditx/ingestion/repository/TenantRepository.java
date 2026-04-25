package com.auditx.ingestion.repository;

import com.auditx.ingestion.model.TenantDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface TenantRepository extends ReactiveMongoRepository<TenantDocument, String> {
    Mono<TenantDocument> findByApiKeyHash(String apiKeyHash);
}
