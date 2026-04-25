package com.auditx.report.dto;

public record AuditEventSummary(
        String eventId,
        String userId,
        String action,
        String sourceIp,
        String outcome,
        java.time.Instant timestamp,
        Double riskScore
) {}
