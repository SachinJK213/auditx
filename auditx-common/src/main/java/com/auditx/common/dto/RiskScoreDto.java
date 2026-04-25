package com.auditx.common.dto;

import java.time.Instant;
import java.util.List;

public record RiskScoreDto(
        String eventId,
        String tenantId,
        Double score,
        List<String> ruleMatches,
        Instant computedAt
) {}
