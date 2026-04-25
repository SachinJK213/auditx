package com.auditx.compliance.service;

import com.auditx.common.dto.PolicyViolationDto;
import com.auditx.common.util.IdempotencyKeyGenerator;
import com.auditx.compliance.model.ComplianceRecordDocument;
import com.auditx.compliance.repository.ComplianceRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
public class ComplianceService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceService.class);

    private final ComplianceRecordRepository complianceRecordRepository;

    public ComplianceService(ComplianceRecordRepository complianceRecordRepository) {
        this.complianceRecordRepository = complianceRecordRepository;
    }

    public Mono<Void> recordViolation(PolicyViolationDto violation) {
        if (violation.complianceFrameworks() == null || violation.complianceFrameworks().isEmpty()) {
            return Mono.empty();
        }

        return Flux.fromIterable(violation.complianceFrameworks())
                .flatMap(framework -> {
                    ComplianceRecordDocument record = new ComplianceRecordDocument();
                    record.setRecordId(IdempotencyKeyGenerator.generate());
                    record.setTenantId(violation.tenantId());
                    record.setFramework(framework);
                    record.setRuleId(violation.ruleId());
                    record.setRuleName(violation.ruleName());
                    record.setEventId(violation.eventId());
                    record.setUserId(violation.userId());
                    record.setSeverity(violation.severity());
                    record.setStatus("OPEN");
                    record.setOccurredAt(violation.occurredAt() != null ? violation.occurredAt() : Instant.now());
                    return complianceRecordRepository.save(record)
                            .doOnSuccess(saved -> log.info(
                                    "{\"recordId\":\"{}\",\"framework\":\"{}\",\"ruleId\":\"{}\",\"tenantId\":\"{}\"}",
                                    saved.getRecordId(), framework, violation.ruleId(), violation.tenantId()));
                })
                .then();
    }

    public Mono<ComplianceRecordDocument> resolve(String tenantId, String recordId) {
        return complianceRecordRepository.findByRecordId(recordId)
                .filter(r -> r.getTenantId().equals(tenantId))
                .flatMap(record -> {
                    record.setStatus("RESOLVED");
                    record.setResolvedAt(Instant.now());
                    return complianceRecordRepository.save(record);
                });
    }
}
