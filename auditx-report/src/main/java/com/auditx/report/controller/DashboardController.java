package com.auditx.report.controller;

import com.auditx.report.model.AlertDocument;
import com.auditx.report.model.AuditEventDocument;
import com.auditx.report.repository.AlertRepository;
import com.auditx.report.repository.AuditEventRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dashboard API — provides JSON data for the AUDITX UI dashboard.
 * All endpoints are scoped to a tenantId query parameter.
 */
@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    private final AuditEventRepository auditEventRepository;
    private final AlertRepository alertRepository;

    public DashboardController(AuditEventRepository auditEventRepository,
                               AlertRepository alertRepository) {
        this.auditEventRepository = auditEventRepository;
        this.alertRepository = alertRepository;
    }

    /**
     * GET /api/dashboard/summary?tenantId=...&days=7
     * Returns high-level stats: total events, high-risk count, alert count, events by action.
     */
    @GetMapping("/summary")
    public Mono<ResponseEntity<Map<String, Object>>> summary(
            @RequestParam(name = "tenantId") String tenantId,
            @RequestParam(name = "days", defaultValue = "7") int days) {

        if (tenantId == null || tenantId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        Instant from = Instant.now().minus(days, ChronoUnit.DAYS);
        Instant to = Instant.now();

        Mono<List<AuditEventDocument>> eventsMono =
                auditEventRepository.findByTenantIdAndTimestampBetween(tenantId, from, to).collectList();
        Mono<List<AlertDocument>> alertsMono =
                alertRepository.findByTenantIdAndCreatedAtBetween(tenantId, from, to).collectList();

        return Mono.zip(eventsMono, alertsMono).map(tuple -> {
            List<AuditEventDocument> events = tuple.getT1();
            List<AlertDocument> alerts = tuple.getT2();

            long highRiskCount = events.stream()
                    .filter(e -> e.getRiskScore() != null && e.getRiskScore() >= 70)
                    .count();

            Map<String, Long> byAction = events.stream()
                    .filter(e -> e.getAction() != null)
                    .collect(Collectors.groupingBy(AuditEventDocument::getAction, Collectors.counting()));

            Map<String, Long> byOutcome = events.stream()
                    .filter(e -> e.getOutcome() != null)
                    .collect(Collectors.groupingBy(AuditEventDocument::getOutcome, Collectors.counting()));

            // Average risk score
            OptionalDouble avgScore = events.stream()
                    .filter(e -> e.getRiskScore() != null)
                    .mapToDouble(AuditEventDocument::getRiskScore)
                    .average();

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("tenantId", tenantId);
            summary.put("periodDays", days);
            summary.put("totalEvents", events.size());
            summary.put("highRiskEvents", highRiskCount);
            summary.put("totalAlerts", alerts.size());
            summary.put("avgRiskScore", avgScore.isPresent() ? Math.round(avgScore.getAsDouble() * 10.0) / 10.0 : 0);
            summary.put("eventsByAction", byAction);
            summary.put("eventsByOutcome", byOutcome);

            return ResponseEntity.ok(summary);
        });
    }

    /**
     * GET /api/dashboard/events?tenantId=...&days=7&limit=50
     * Returns recent events with risk scores.
     */
    @GetMapping("/events")
    public Mono<ResponseEntity<List<Map<String, Object>>>> events(
            @RequestParam(name = "tenantId") String tenantId,
            @RequestParam(name = "days", defaultValue = "7") int days,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {

        if (tenantId == null || tenantId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        Instant from = Instant.now().minus(days, ChronoUnit.DAYS);
        Instant to = Instant.now();

        return auditEventRepository.findByTenantIdAndTimestampBetween(tenantId, from, to)
                .take(limit)
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("eventId", e.getEventId());
                    row.put("userId", e.getUserId());
                    row.put("action", e.getAction());
                    row.put("sourceIp", e.getSourceIp());
                    row.put("outcome", e.getOutcome());
                    row.put("timestamp", e.getTimestamp() != null ? e.getTimestamp().toString() : null);
                    row.put("riskScore", e.getRiskScore() != null ? e.getRiskScore() : 0);
                    row.put("riskLevel", riskLevel(e.getRiskScore()));
                    return row;
                })
                .collectList()
                .map(ResponseEntity::ok);
    }

    /**
     * GET /api/dashboard/alerts?tenantId=...&days=7
     * Returns recent alerts.
     */
    @GetMapping("/alerts")
    public Mono<ResponseEntity<List<Map<String, Object>>>> alerts(
            @RequestParam(name = "tenantId") String tenantId,
            @RequestParam(name = "days", defaultValue = "30") int days) {

        if (tenantId == null || tenantId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        Instant from = Instant.now().minus(days, ChronoUnit.DAYS);
        Instant to = Instant.now();

        return alertRepository.findByTenantIdAndCreatedAtBetween(tenantId, from, to)
                .map(a -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("alertId", a.getAlertId());
                    row.put("eventId", a.getEventId());
                    row.put("riskScore", a.getRiskScore() != null ? a.getRiskScore() : 0);
                    row.put("riskLevel", riskLevel(a.getRiskScore()));
                    row.put("status", a.getStatus());
                    row.put("createdAt", a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
                    return row;
                })
                .collectList()
                .map(ResponseEntity::ok);
    }

    private String riskLevel(Double score) {
        if (score == null || score < 30) return "LOW";
        if (score < 60) return "MEDIUM";
        if (score < 80) return "HIGH";
        return "CRITICAL";
    }
}
