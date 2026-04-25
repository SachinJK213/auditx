package com.auditx.common.dto;

import java.time.Instant;

public record EnrichedEventDto(
        String eventId,
        String tenantId,
        String userId,
        String action,
        String sourceIp,
        String outcome,
        Instant timestamp,
        Double riskScore,
        String country,
        String countryCode,
        String city,
        Double lat,
        Double lon,
        String isp,
        boolean privateIp,
        Instant enrichedAt
) {}
