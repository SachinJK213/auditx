package com.auditx.common.dto;

import java.time.Instant;

public record NotificationDto(
        String alertId,
        String tenantId,
        String userId,
        Double riskScore,
        String channel,
        String message,
        Instant sentAt
) {}
