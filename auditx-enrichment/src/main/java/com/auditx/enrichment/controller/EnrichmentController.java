package com.auditx.enrichment.controller;

import com.auditx.enrichment.model.EnrichedEventDocument;
import com.auditx.enrichment.repository.EnrichedEventRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/api/enrichment")
public class EnrichmentController {

    private final EnrichedEventRepository repository;

    public EnrichmentController(EnrichedEventRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/events")
    public Flux<EnrichedEventDocument> getEvents(
            @RequestParam String tenantId,
            @RequestParam(defaultValue = "7") int days) {
        return repository.findByTenantIdAndEnrichedAtAfter(tenantId,
                Instant.now().minus(days, ChronoUnit.DAYS));
    }
}
