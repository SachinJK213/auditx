package com.auditx.sdk.publisher;

import com.auditx.common.dto.RawEventDto;
import com.auditx.common.enums.EventOutcome;
import com.auditx.common.enums.PayloadType;
import com.auditx.sdk.AuditxProperties;
import com.auditx.sdk.sender.AuditxEventSender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class AuditxLoginEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuditxLoginEventPublisher.class);

    private final AuditxEventSender sender;
    private final AuditxProperties properties;
    private final ObjectMapper objectMapper;

    public AuditxLoginEventPublisher(AuditxEventSender sender,
                                      AuditxProperties properties,
                                      ObjectMapper objectMapper) {
        this.sender = sender;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Publish a login event to the AUDITX ingestion endpoint.
     *
     * @param userId    the user who attempted login
     * @param outcome   SUCCESS or FAILURE
     * @param sourceIp  the originating IP address
     * @param timestamp the time of the login attempt
     */
    public void publishLoginEvent(String userId,
                                   EventOutcome outcome,
                                   String sourceIp,
                                   Instant timestamp) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "userId", userId != null ? userId : "unknown",
                    "outcome", outcome != null ? outcome.name() : "UNKNOWN",
                    "sourceIp", sourceIp != null ? sourceIp : "unknown",
                    "timestamp", (timestamp != null ? timestamp : Instant.now()).toString()
            ));

            RawEventDto event = new RawEventDto(
                    UUID.randomUUID().toString(),
                    properties.getTenantId(),
                    payload,
                    PayloadType.STRUCTURED,
                    null,
                    Instant.now()
            );

            sender.send(event);
        } catch (JsonProcessingException e) {
            log.warn("AuditX SDK: failed to serialize login event payload for userId={}", userId, e);
        }
    }
}
