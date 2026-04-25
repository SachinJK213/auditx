package com.auditx.common.dto;

import java.time.Instant;
import java.util.List;

public record StructuredEventDto(
        String eventId,
        String tenantId,
        String userId,
        String action,
        String sourceIp,
        String outcome,
        Instant timestamp,
        Double riskScore,
        List<String> ruleMatches,
        Instant computedAt
) {}
