package com.auditx.common.dto;

import java.time.Instant;
import java.util.List;

public record AlertDto(
        String alertId,
        String tenantId,
        String eventId,
        String userId,
        Double riskScore,
        List<String> ruleMatches,
        String status,
        Instant createdAt
) {}
