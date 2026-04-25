package com.auditx.report.dto;

public record ReportRequest(
        String tenantId,
        java.time.LocalDate startDate,
        java.time.LocalDate endDate,
        String reportType
) {}
