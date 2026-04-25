package com.auditx.alert.controller;

import com.auditx.alert.model.AlertDocument;
import com.auditx.alert.repository.AlertRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertRepository alertRepository;

    public AlertController(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @PostMapping("/{alertId}/acknowledge")
    public Mono<ResponseEntity<AlertDocument>> acknowledge(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable String alertId,
            @RequestBody Map<String, String> body) {
        String acknowledgedBy = body.getOrDefault("acknowledgedBy", "unknown");
        return alertRepository.findByTenantIdAndAlertId(tenantId, alertId)
                .flatMap(alert -> {
                    alert.setStatus("ACKNOWLEDGED");
                    alert.setAcknowledgedBy(acknowledgedBy);
                    alert.setAcknowledgedAt(Instant.now());
                    return alertRepository.save(alert);
                })
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/{alertId}")
    public Mono<ResponseEntity<AlertDocument>> getAlert(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable String alertId) {
        return alertRepository.findByTenantIdAndAlertId(tenantId, alertId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
