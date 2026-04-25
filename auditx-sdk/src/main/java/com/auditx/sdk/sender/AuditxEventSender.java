package com.auditx.sdk.sender;

import com.auditx.common.dto.RawEventDto;
import com.auditx.common.util.IdempotencyKeyGenerator;
import com.auditx.sdk.AuditxProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuditxEventSender {

    private static final Logger log = LoggerFactory.getLogger(AuditxEventSender.class);

    private final AuditxProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ExecutorService virtualThreadExecutor;

    public AuditxEventSender(AuditxProperties properties,
                              RestTemplate restTemplate,
                              ObjectMapper objectMapper) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Send an event, generating a unique idempotency key first.
     * If async=true, submits to virtual thread executor; otherwise sends synchronously.
     */
    public void send(RawEventDto event) {
        String idempotencyKey = IdempotencyKeyGenerator.generate();
        RawEventDto enriched = new RawEventDto(
                event.eventId(),
                event.tenantId(),
                event.payload(),
                event.payloadType(),
                idempotencyKey,
                event.timestamp() != null ? event.timestamp() : Instant.now()
        );

        if (properties.isAsync()) {
            virtualThreadExecutor.submit(() -> sendSync(enriched));
        } else {
            sendSync(enriched);
        }
    }

    /**
     * Synchronous send with exponential backoff retry.
     */
    void sendSync(RawEventDto event) {
        int maxAttempts = properties.getRetry().getMaxAttempts();
        Exception lastException = null;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                if (attempt > 0) {
                    long backoffMs = 500L * (1L << (attempt - 1));
                    log.info("Retrying event send: attempt={}, endpoint={}, eventId={}",
                            attempt + 1, properties.getEndpoint(), event.eventId());
                    Thread.sleep(backoffMs);
                }
                doPost(event);
                return; // success
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logStructuredError(event, ie, attempt + 1);
                return;
            } catch (Exception ex) {
                lastException = ex;
                log.warn("Event send attempt {} failed: eventId={}, error={}",
                        attempt + 1, event.eventId(), ex.getMessage());
            }
        }

        logStructuredError(event, lastException, maxAttempts);
    }

    private void doPost(RawEventDto event) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", properties.getApiKey());

        HttpEntity<RawEventDto> request = new HttpEntity<>(event, headers);
        restTemplate.postForEntity(properties.getEndpoint(), request, Void.class);
    }

    private void logStructuredError(RawEventDto event, Exception cause, int attemptCount) {
        try {
            String errorJson = objectMapper.writeValueAsString(Map.of(
                    "level", "ERROR",
                    "message", "AuditX SDK: exhausted retries sending event",
                    "eventId", event.eventId() != null ? event.eventId() : "unknown",
                    "tenantId", event.tenantId() != null ? event.tenantId() : "unknown",
                    "endpoint", properties.getEndpoint() != null ? properties.getEndpoint() : "unknown",
                    "attemptCount", attemptCount,
                    "errorMessage", cause != null ? cause.getMessage() : "unknown",
                    "timestamp", Instant.now().toString()
            ));
            log.error(errorJson);
        } catch (JsonProcessingException e) {
            log.error("AuditX SDK: exhausted retries for eventId={}, attempts={}", event.eventId(), attemptCount);
        }
    }
}
