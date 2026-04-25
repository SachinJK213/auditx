package com.auditx.common.dto;

import com.auditx.common.enums.PayloadType;
import java.time.Instant;

public record RawEventDto(
        String eventId,
        String tenantId,
        String payload,
        PayloadType payloadType,
        String idempotencyKey,
        Instant timestamp
) {}
