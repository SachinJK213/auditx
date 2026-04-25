package com.auditx.ingestion.controller;

import com.auditx.common.dto.RawEventDto;
import com.auditx.common.enums.PayloadType;
import com.auditx.common.exception.TenantNotFoundException;
import com.auditx.ingestion.model.WebhookSourceDocument;
import com.auditx.ingestion.repository.WebhookSourceRepository;
import com.auditx.ingestion.service.IngestionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private final WebhookSourceRepository sourceRepository;
    private final IngestionService ingestionService;

    public WebhookController(WebhookSourceRepository sourceRepository,
                             IngestionService ingestionService) {
        this.sourceRepository = sourceRepository;
        this.ingestionService = ingestionService;
    }

    @PostMapping("/{sourceId}")
    public Mono<ResponseEntity<Map<String, String>>> receiveWebhook(
            @PathVariable String sourceId,
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        return sourceRepository.findBySourceId(sourceId)
                .switchIfEmpty(Mono.error(new TenantNotFoundException("Webhook source not found: " + sourceId)))
                .flatMap(source -> {
                    if (!source.isActive()) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .<Map<String, String>>body(Map.of("error", "Source disabled")));
                    }

                    // Map JSON fields to AuditX payload using common field aliases
                    String userId   = getStr(payload, "userId", "user", "actor", "username", "sub");
                    String action   = getStr(payload, "action", "event", "type", "eventType");
                    String sourceIp = getStr(payload, "sourceIp", "ip", "ipAddress", "clientIp");
                    String outcome  = getStr(payload, "outcome", "result", "status");
                    String ts       = getStr(payload, "timestamp", "time", "createdAt", "eventTime");

                    if (outcome != null) {
                        outcome = outcome.toUpperCase().contains("FAIL") || outcome.toUpperCase().contains("ERROR")
                                ? "FAILURE" : "SUCCESS";
                    } else {
                        outcome = "UNKNOWN";
                    }

                    String rawPayload = String.format(
                            "timestamp=%s userId=%s action=%s sourceIp=%s tenantId=%s outcome=%s",
                            ts != null ? ts : Instant.now().toString(),
                            userId != null ? userId : "unknown",
                            action != null ? action : "WEBHOOK_EVENT",
                            sourceIp != null ? sourceIp : "0.0.0.0",
                            source.getTenantId(),
                            outcome
                    );

                    RawEventDto dto = new RawEventDto(
                            null, source.getTenantId(), rawPayload,
                            PayloadType.RAW, "webhook-" + System.currentTimeMillis(), Instant.now()
                    );

                    return ingestionService.ingest(dto, source.getTenantId())
                            .map(eventId -> ResponseEntity.accepted()
                                    .<Map<String, String>>body(Map.of("eventId", eventId, "sourceId", sourceId)));
                })
                .onErrorResume(TenantNotFoundException.class, ex ->
                        Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .<Map<String, String>>body(Map.of("error", ex.getMessage()))));
    }

    @PostMapping("/sources")
    public Mono<ResponseEntity<WebhookSourceDocument>> registerSource(
            @RequestBody WebhookSourceDocument source) {
        source.setCreatedAt(Instant.now());
        source.setActive(true);
        return sourceRepository.save(source)
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved));
    }

    private String getStr(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val != null) return val.toString();
        }
        return null;
    }
}
