package com.auditx.report.service;

import com.auditx.report.dto.AlertSummary;
import com.auditx.report.dto.AuditEventSummary;
import com.auditx.report.dto.ReportData;
import com.auditx.report.dto.ReportRequest;
import com.auditx.report.repository.AlertRepository;
import com.auditx.report.repository.AuditEventRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportDataAssembler {

    private static final double HIGH_RISK_THRESHOLD = 70.0;

    private final AuditEventRepository auditEventRepository;
    private final AlertRepository alertRepository;

    public ReportDataAssembler(AuditEventRepository auditEventRepository,
                               AlertRepository alertRepository) {
        this.auditEventRepository = auditEventRepository;
        this.alertRepository = alertRepository;
    }

    public Mono<ReportData> assemble(ReportRequest request) {
        Instant start = request.startDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = request.endDate().atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant();

        Mono<List<AuditEventSummary>> allEventsMono = auditEventRepository
                .findByTenantIdAndTimestampBetween(request.tenantId(), start, end)
                .map(doc -> new AuditEventSummary(
                        doc.getEventId(),
                        doc.getUserId(),
                        doc.getAction(),
                        doc.getSourceIp(),
                        doc.getOutcome(),
                        doc.getTimestamp(),
                        doc.getRiskScore()))
                .collectList();

        Mono<List<AuditEventSummary>> highRiskMono = auditEventRepository
                .findByTenantIdAndRiskScoreGreaterThanEqualAndTimestampBetween(
                        request.tenantId(), HIGH_RISK_THRESHOLD, start, end)
                .map(doc -> new AuditEventSummary(
                        doc.getEventId(),
                        doc.getUserId(),
                        doc.getAction(),
                        doc.getSourceIp(),
                        doc.getOutcome(),
                        doc.getTimestamp(),
                        doc.getRiskScore()))
                .collectList();

        Mono<List<AlertSummary>> alertsMono = alertRepository
                .findByTenantIdAndCreatedAtBetween(request.tenantId(), start, end)
                .map(doc -> new AlertSummary(
                        doc.getAlertId(),
                        doc.getEventId(),
                        doc.getRiskScore(),
                        doc.getStatus(),
                        doc.getCreatedAt()))
                .collectList();

        return Mono.zip(allEventsMono, highRiskMono, alertsMono)
                .map(tuple -> {
                    List<AuditEventSummary> allEvents = tuple.getT1();
                    List<AuditEventSummary> highRisk = tuple.getT2();
                    List<AlertSummary> alerts = tuple.getT3();

                    // Requirement 8.6: empty range → report with zero counts, not an error
                    if (allEvents.isEmpty()) {
                        return new ReportData(
                                request.tenantId(),
                                request.startDate(),
                                request.endDate(),
                                request.reportType(),
                                0L,
                                Collections.emptyMap(),
                                Collections.emptyList(),
                                alerts);
                    }

                    long totalEvents = allEvents.size();
                    Map<String, Long> eventsByAction = allEvents.stream()
                            .filter(e -> e.action() != null)
                            .collect(Collectors.groupingBy(AuditEventSummary::action, Collectors.counting()));

                    return new ReportData(
                            request.tenantId(),
                            request.startDate(),
                            request.endDate(),
                            request.reportType(),
                            totalEvents,
                            eventsByAction,
                            highRisk,
                            alerts);
                });
    }
}
