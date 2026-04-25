package com.auditx.common.dto;

import java.time.Instant;
import java.util.List;

public record PolicyViolationDto(
        String violationId,
        String tenantId,
        String eventId,
        String userId,
        String ruleId,
        String ruleName,
        String severity,
        String condition,
        List<String> complianceFrameworks,
        Instant occurredAt
) {}
