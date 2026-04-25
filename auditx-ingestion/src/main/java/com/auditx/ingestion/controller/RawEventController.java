package com.auditx.ingestion.controller;

import com.auditx.common.dto.RawEventDto;
import com.auditx.ingestion.service.IngestionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/events")
public class RawEventController {

    private static final String TENANT_ID_ATTR = "X-Tenant-Id";

    private final IngestionService ingestionService;

    public RawEventController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/raw")
    public Mono<ResponseEntity<Map<String, String>>> ingestRawEvent(
            @RequestBody RawEventDto dto,
            ServerWebExchange exchange) {

        String tenantId = exchange.getAttribute(TENANT_ID_ATTR);
        if (tenantId == null || tenantId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Bad Request", "message", "Tenant ID could not be resolved")));
        }

        return ingestionService.ingest(dto, tenantId)
                .map(eventId -> ResponseEntity.status(HttpStatus.ACCEPTED)
                        .<Map<String, String>>body(Map.of("eventId", eventId)));
    }
}
