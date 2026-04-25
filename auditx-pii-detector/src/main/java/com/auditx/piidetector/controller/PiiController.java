package com.auditx.piidetector.controller;

import com.auditx.piidetector.model.PiiFindingDocument;
import com.auditx.piidetector.repository.PiiFindingRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@RestController
@RequestMapping("/api/pii")
public class PiiController {

    private final PiiFindingRepository repository;

    public PiiController(PiiFindingRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/findings")
    public Flux<PiiFindingDocument> getFindings(
            @RequestParam String tenantId,
            @RequestParam(defaultValue = "7") int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        return repository.findByTenantIdAndScannedAtAfter(tenantId, since);
    }

    @GetMapping("/summary")
    public Mono<Map<String, Object>> getSummary(
            @RequestParam String tenantId,
            @RequestParam(defaultValue = "7") int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        return repository.findByTenantIdAndScannedAtAfter(tenantId, since)
            .collectList()
            .map(findings -> Map.of(
                "tenantId", tenantId,
                "days", days,
                "eventsWithPii", findings.size(),
                "totalMatches", findings.stream().mapToInt(PiiFindingDocument::getMatchCount).sum()
            ));
    }
}
