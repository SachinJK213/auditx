package com.auditx.livestream.dto;

import java.time.Instant;
import java.util.List;

public record LiveEventDto(
        String eventId,
        String tenantId,
        String userId,
        String action,
        String sourceIp,
        String outcome,
        Instant timestamp,
        Double riskScore,
        String riskLevel,
        List<String> ruleMatches,
        String parseStatus,
        Instant receivedAt
) {
    public static String toRiskLevel(Double score) {
        if (score == null) return "UNKNOWN";
        if (score >= 80) return "CRITICAL";
        if (score >= 60) return "HIGH";
        if (score >= 30) return "MEDIUM";
        return "LOW";
    }
}
