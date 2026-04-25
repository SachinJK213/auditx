package com.auditx.report.dto;

public record AlertSummary(
        String alertId,
        String eventId,
        Double riskScore,
        String status,
        java.time.Instant createdAt
) {}
