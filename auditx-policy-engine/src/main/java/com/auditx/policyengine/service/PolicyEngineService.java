package com.auditx.policyengine.service;

import com.auditx.common.constants.KafkaTopics;
import com.auditx.common.dto.PolicyViolationDto;
import com.auditx.common.dto.StructuredEventDto;
import com.auditx.common.util.IdempotencyKeyGenerator;
import com.auditx.policyengine.model.PolicyRuleDocument;
import com.auditx.policyengine.repository.PolicyRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Service
public class PolicyEngineService {

    private static final Logger log = LoggerFactory.getLogger(PolicyEngineService.class);

    private final PolicyRuleRepository policyRuleRepository;
    private final KafkaTemplate<String, PolicyViolationDto> violationKafkaTemplate;
    private final ExpressionParser spelParser = new SpelExpressionParser();

    public PolicyEngineService(PolicyRuleRepository policyRuleRepository,
                               KafkaTemplate<String, PolicyViolationDto> violationKafkaTemplate) {
        this.policyRuleRepository = policyRuleRepository;
        this.violationKafkaTemplate = violationKafkaTemplate;
    }

    public Mono<Void> evaluate(StructuredEventDto event) {
        return policyRuleRepository.findByTenantIdAndActiveTrue(event.tenantId())
                .filter(rule -> matchesCondition(rule, event))
                .doOnNext(rule -> publishViolation(event, rule))
                .then();
    }

    private boolean matchesCondition(PolicyRuleDocument rule, StructuredEventDto event) {
        try {
            StandardEvaluationContext ctx = new StandardEvaluationContext();
            ctx.setVariable("userId", event.userId());
            ctx.setVariable("action", event.action());
            ctx.setVariable("outcome", event.outcome());
            ctx.setVariable("sourceIp", event.sourceIp());
            ctx.setVariable("riskScore", event.riskScore() != null ? event.riskScore() : 0.0);
            ctx.setVariable("tenantId", event.tenantId());

            Boolean result = spelParser.parseExpression(rule.getCondition()).getValue(ctx, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (Exception ex) {
            log.warn("{\"ruleId\":\"{}\",\"eventId\":\"{}\",\"warn\":\"condition eval failed: {}\"}",
                    rule.getRuleId(), event.eventId(), ex.getMessage());
            return false;
        }
    }

    private void publishViolation(StructuredEventDto event, PolicyRuleDocument rule) {
        PolicyViolationDto violation = new PolicyViolationDto(
                IdempotencyKeyGenerator.generate(),
                event.tenantId(),
                event.eventId(),
                event.userId(),
                rule.getRuleId(),
                rule.getRuleName(),
                rule.getSeverity(),
                rule.getCondition(),
                rule.getComplianceFrameworks() != null ? rule.getComplianceFrameworks() : List.of(),
                Instant.now()
        );
        violationKafkaTemplate.send(KafkaTopics.POLICY_VIOLATIONS, violation.violationId(), violation);
        log.info("{\"violationId\":\"{}\",\"ruleId\":\"{}\",\"eventId\":\"{}\",\"tenantId\":\"{}\",\"severity\":\"{}\"}",
                violation.violationId(), rule.getRuleId(), event.eventId(), event.tenantId(), rule.getSeverity());
    }
}
