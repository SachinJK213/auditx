package com.auditx.policyengine.controller;

import com.auditx.common.util.IdempotencyKeyGenerator;
import com.auditx.policyengine.model.PolicyRuleDocument;
import com.auditx.policyengine.repository.PolicyRuleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/policy-rules")
public class PolicyRuleController {

    private final PolicyRuleRepository policyRuleRepository;

    public PolicyRuleController(PolicyRuleRepository policyRuleRepository) {
        this.policyRuleRepository = policyRuleRepository;
    }

    @GetMapping
    public Flux<PolicyRuleDocument> list(@RequestHeader("X-Tenant-Id") String tenantId) {
        return policyRuleRepository.findByTenantIdAndActiveTrue(tenantId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<PolicyRuleDocument> create(@RequestHeader("X-Tenant-Id") String tenantId,
                                           @RequestBody PolicyRuleDocument rule) {
        rule.setTenantId(tenantId);
        if (rule.getRuleId() == null) rule.setRuleId(IdempotencyKeyGenerator.generate());
        rule.setActive(true);
        return policyRuleRepository.save(rule);
    }

    @PutMapping("/{ruleId}")
    public Mono<PolicyRuleDocument> update(@RequestHeader("X-Tenant-Id") String tenantId,
                                           @PathVariable String ruleId,
                                           @RequestBody PolicyRuleDocument rule) {
        return policyRuleRepository.findByTenantIdAndRuleId(tenantId, ruleId)
                .flatMap(existing -> {
                    rule.setId(existing.getId());
                    rule.setTenantId(tenantId);
                    rule.setRuleId(ruleId);
                    return policyRuleRepository.save(rule);
                });
    }

    @DeleteMapping("/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@RequestHeader("X-Tenant-Id") String tenantId,
                             @PathVariable String ruleId) {
        return policyRuleRepository.findByTenantIdAndRuleId(tenantId, ruleId)
                .flatMap(existing -> {
                    existing.setActive(false);
                    return policyRuleRepository.save(existing);
                })
                .then();
    }
}
