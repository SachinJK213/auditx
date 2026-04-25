package com.auditx.llm.controller;

import com.auditx.llm.dto.ExplainRequest;
import com.auditx.llm.dto.LlmResponse;
import com.auditx.llm.dto.QueryRequest;
import com.auditx.llm.dto.SummarizeRequest;
import com.auditx.llm.model.AlertDocument;
import com.auditx.llm.repository.AlertRepository;
import com.auditx.llm.repository.AuditEventRepository;
import com.auditx.llm.service.LlmService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/llm")
public class LlmController {

    private final LlmService llmService;
    private final AuditEventRepository auditEventRepository;
    private final AlertRepository alertRepository;
    private final ReactiveMongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;

    public LlmController(LlmService llmService,
                         AuditEventRepository auditEventRepository,
                         AlertRepository alertRepository,
                         ReactiveMongoTemplate mongoTemplate,
                         ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.auditEventRepository = auditEventRepository;
        this.alertRepository = alertRepository;
        this.mongoTemplate = mongoTemplate;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/explain")
    public Mono<ResponseEntity<LlmResponse>> explain(@RequestBody ExplainRequest request) {
        if (request.tenantId() == null || request.tenantId().isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return auditEventRepository.findByEventIdAndTenantId(request.eventId(), request.tenantId())
                .flatMap(event -> {
                    Map<String, String> variables = Map.of(
                            "eventId", nvl(event.getEventId()),
                            "tenantId", nvl(event.getTenantId()),
                            "userId", nvl(event.getUserId()),
                            "action", nvl(event.getAction()),
                            "sourceIp", nvl(event.getSourceIp()),
                            "outcome", nvl(event.getOutcome()),
                            "timestamp", event.getTimestamp() != null ? event.getTimestamp().toString() : "",
                            "riskScore", event.getRiskScore() != null ? event.getRiskScore().toString() : "0"
                    );
                    return llmService.complete("explain", variables, request.tenantId(), "explain");
                })
                .map(result -> ResponseEntity.ok(new LlmResponse(result)))
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    @PostMapping("/query")
    public Mono<ResponseEntity<Object>> query(@RequestBody QueryRequest request) {
        if (request.tenantId() == null || request.tenantId().isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        Map<String, String> variables = Map.of(
                "question", nvl(request.question()),
                "tenantId", request.tenantId()
        );

        return llmService.complete("query", variables, request.tenantId(), "query")
                .flatMap(llmJson -> {
                    try {
                        // Parse LLM response as MongoDB query document
                        Document queryDoc = Document.parse(llmJson);
                        // Enforce tenantId filter — always override/merge
                        queryDoc.put("tenantId", request.tenantId());

                        BasicQuery mongoQuery = new BasicQuery(queryDoc.toJson());
                        return mongoTemplate.find(mongoQuery, org.bson.Document.class, "audit_events")
                                .collectList()
                                .map(results -> ResponseEntity.ok((Object) results));
                    } catch (Exception e) {
                        return Mono.just(ResponseEntity.ok(
                                (Object) List.of("Query parse error: " + e.getMessage())));
                    }
                });
    }

    @PostMapping("/summarize")
    public Mono<ResponseEntity<LlmResponse>> summarize(@RequestBody SummarizeRequest request) {
        if (request.tenantId() == null || request.tenantId().isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return alertRepository.findByAlertIdInAndTenantId(request.alertIds(), request.tenantId())
                .collectList()
                .flatMap(alerts -> {
                    String alertsJson;
                    try {
                        alertsJson = objectMapper.writeValueAsString(alerts);
                    } catch (JsonProcessingException e) {
                        alertsJson = alerts.stream()
                                .map(AlertDocument::getAlertId)
                                .collect(Collectors.joining(", "));
                    }
                    Map<String, String> variables = Map.of(
                            "tenantId", request.tenantId(),
                            "alerts", alertsJson
                    );
                    return llmService.complete("summarize", variables, request.tenantId(), "summarize");
                })
                .map(result -> ResponseEntity.ok(new LlmResponse(result)));
    }

    private String nvl(String value) {
        return value != null ? value : "";
    }
}
