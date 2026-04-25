package com.auditx.alert.repository;

import com.auditx.alert.model.TenantDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface TenantRepository extends ReactiveMongoRepository<TenantDocument, String> {
    Mono<TenantDocument> findByTenantId(String tenantId);
}
