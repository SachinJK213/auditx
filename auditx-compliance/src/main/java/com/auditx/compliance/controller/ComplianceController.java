package com.auditx.compliance.controller;

import com.auditx.compliance.model.ComplianceRecordDocument;
import com.auditx.compliance.repository.ComplianceRecordRepository;
import com.auditx.compliance.service.ComplianceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/compliance")
public class ComplianceController {

    private final ComplianceRecordRepository complianceRecordRepository;
    private final ComplianceService complianceService;

    public ComplianceController(ComplianceRecordRepository complianceRecordRepository,
                                ComplianceService complianceService) {
        this.complianceRecordRepository = complianceRecordRepository;
        this.complianceService = complianceService;
    }

    @GetMapping("/{framework}/records")
    public Flux<ComplianceRecordDocument> getRecords(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable String framework,
            @RequestParam(defaultValue = "OPEN") String status) {
        return complianceRecordRepository.findByTenantIdAndFrameworkAndStatus(tenantId, framework.toUpperCase(), status);
    }

    @GetMapping("/{framework}/summary")
    public Mono<Map<String, Object>> getSummary(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable String framework) {
        return complianceRecordRepository.countByTenantIdAndFrameworkAndStatus(tenantId, framework.toUpperCase(), "OPEN")
                .map(count -> Map.of(
                        "framework", framework.toUpperCase(),
                        "tenantId", tenantId,
                        "openViolations", count
                ));
    }

    @PostMapping("/records/{recordId}/resolve")
    public Mono<ResponseEntity<ComplianceRecordDocument>> resolve(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable String recordId) {
        return complianceService.resolve(tenantId, recordId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
