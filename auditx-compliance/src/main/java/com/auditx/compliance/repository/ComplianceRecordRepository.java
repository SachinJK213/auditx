package com.auditx.compliance.repository;

import com.auditx.compliance.model.ComplianceRecordDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ComplianceRecordRepository extends ReactiveMongoRepository<ComplianceRecordDocument, String> {
    Flux<ComplianceRecordDocument> findByTenantIdAndFramework(String tenantId, String framework);
    Flux<ComplianceRecordDocument> findByTenantIdAndFrameworkAndStatus(String tenantId, String framework, String status);
    Mono<Long> countByTenantIdAndFrameworkAndStatus(String tenantId, String framework, String status);
    Mono<ComplianceRecordDocument> findByRecordId(String recordId);
}
