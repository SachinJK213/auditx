package com.auditx.riskengine.service;

import com.auditx.common.constants.KafkaTopics;
import com.auditx.common.dto.AlertDto;
import com.auditx.common.dto.StructuredEventDto;
import com.auditx.common.util.IdempotencyKeyGenerator;
import com.auditx.riskengine.repository.AuditEventRepository;
import com.auditx.riskengine.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
public class RiskEngineService {

    private static final Logger log = LoggerFactory.getLogger(RiskEngineService.class);

    private final RiskScoringEngine riskScoringEngine;
    private final AuditEventRepository auditEventRepository;
    private final ReactiveMongoTemplate mongoTemplate;
    private final TenantRepository tenantRepository;
    private final KafkaTemplate<String, AlertDto> alertKafkaTemplate;
    private final UserRiskAggregationService userRiskAggregationService;

    public RiskEngineService(RiskScoringEngine riskScoringEngine,
                             AuditEventRepository auditEventRepository,
                             ReactiveMongoTemplate mongoTemplate,
                             TenantRepository tenantRepository,
                             KafkaTemplate<String, AlertDto> alertKafkaTemplate,
                             UserRiskAggregationService userRiskAggregationService) {
        this.riskScoringEngine = riskScoringEngine;
        this.auditEventRepository = auditEventRepository;
        this.mongoTemplate = mongoTemplate;
        this.tenantRepository = tenantRepository;
        this.alertKafkaTemplate = alertKafkaTemplate;
        this.userRiskAggregationService = userRiskAggregationService;
    }

    public Mono<Void> process(StructuredEventDto event) {
        // 1. Idempotency check: skip if riskScore already computed
        return auditEventRepository.findByEventId(event.eventId())
                .flatMap(existing -> {
                    if (existing.getRiskScore() != null) {
                        log.debug("Skipping already-scored event: {}", event.eventId());
                        return Mono.<Void>empty();
                    }
                    return scoreAndPersist(event);
                })
                .switchIfEmpty(Mono.defer(() -> scoreAndPersist(event)));
    }

    private Mono<Void> scoreAndPersist(StructuredEventDto event) {
        // 2. Compute risk score
        return riskScoringEngine.computeScore(event)
                .flatMap(scoreDto -> {
                    // 3. Update audit_events document with $set
                    Query query = Query.query(
                            Criteria.where("eventId").is(event.eventId())
                                    .and("tenantId").is(event.tenantId()));
                    Update update = new Update()
                            .set("riskScore", scoreDto.score())
                            .set("ruleMatches", scoreDto.ruleMatches())
                            .set("computedAt", Instant.now());

                    return mongoTemplate.updateFirst(query, update,
                                    com.auditx.riskengine.model.AuditEventDocument.class)
                            .then(userRiskAggregationService.aggregate(event, scoreDto.score()))
                            .then(Mono.just(scoreDto));
                })
                .flatMap(scoreDto -> {
                    // 4. Load tenant config and check alert threshold
                    return tenantRepository.findByTenantId(event.tenantId())
                            .flatMap(tenant -> {
                                Integer alertThreshold = tenant.getAlertThreshold();
                                if (alertThreshold != null && scoreDto.score() > alertThreshold) {
                                    // 5. Publish alert
                                    AlertDto alert = new AlertDto(
                                            IdempotencyKeyGenerator.generate(),
                                            event.tenantId(),
                                            event.eventId(),
                                            event.userId(),
                                            scoreDto.score(),
                                            scoreDto.ruleMatches(),
                                            "OPEN",
                                            Instant.now()
                                    );
                                    alertKafkaTemplate.send(KafkaTopics.ALERTS, alert.alertId(), alert);
                                    log.info("Alert published for event {} with score {}", event.eventId(), scoreDto.score());
                                }
                                return Mono.<Void>empty();
                            })
                            .switchIfEmpty(Mono.empty());
                });
    }
}
