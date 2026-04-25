package com.auditx.riskengine.repository;

import com.auditx.riskengine.model.UserRiskProfileDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserRiskProfileRepository extends ReactiveMongoRepository<UserRiskProfileDocument, String> {
    Mono<UserRiskProfileDocument> findByTenantIdAndUserId(String tenantId, String userId);
    Flux<UserRiskProfileDocument> findByTenantIdOrderByCumulativeScoreDesc(String tenantId);
}
