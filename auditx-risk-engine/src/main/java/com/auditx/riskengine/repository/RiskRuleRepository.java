package com.auditx.riskengine.repository;

import com.auditx.riskengine.model.RiskRuleDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface RiskRuleRepository extends ReactiveMongoRepository<RiskRuleDocument, String> {
    Flux<RiskRuleDocument> findByTenantIdAndActiveTrue(String tenantId);
}
