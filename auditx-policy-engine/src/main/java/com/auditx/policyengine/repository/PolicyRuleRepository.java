package com.auditx.policyengine.repository;

import com.auditx.policyengine.model.PolicyRuleDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PolicyRuleRepository extends ReactiveMongoRepository<PolicyRuleDocument, String> {
    Flux<PolicyRuleDocument> findByTenantIdAndActiveTrue(String tenantId);
    Mono<PolicyRuleDocument> findByTenantIdAndRuleId(String tenantId, String ruleId);
}
