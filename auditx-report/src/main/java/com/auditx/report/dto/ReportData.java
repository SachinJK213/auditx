package com.auditx.report.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record ReportData(
        String tenantId,
        LocalDate startDate,
        LocalDate endDate,
        String reportType,
        long totalEvents,
        Map<String, Long> eventsByAction,
        List<AuditEventSummary> highRiskEvents,
        List<AlertSummary> alerts
) {}
