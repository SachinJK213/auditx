package com.auditx.common.dto;

import java.time.Instant;
import java.util.List;

public record PiiFindingDto(
        String eventId,
        String tenantId,
        List<PiiMatch> matches,
        Instant scannedAt
) {
    public record PiiMatch(String type, String maskedValue) {}
}
