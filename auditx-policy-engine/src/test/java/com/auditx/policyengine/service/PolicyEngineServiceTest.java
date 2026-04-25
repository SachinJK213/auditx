package com.auditx.policyengine.service;

import com.auditx.common.dto.PolicyViolationDto;
import com.auditx.common.dto.StructuredEventDto;
import com.auditx.policyengine.model.PolicyRuleDocument;
import com.auditx.policyengine.repository.PolicyRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyEngineServiceTest {

    @Mock PolicyRuleRepository policyRuleRepository;
    @Mock KafkaTemplate<String, PolicyViolationDto> kafkaTemplate;

    private PolicyEngineService service;

    @BeforeEach
    void setUp() { service = new PolicyEngineService(policyRuleRepository, kafkaTemplate); }

    private StructuredEventDto event(String action, String outcome, double riskScore) {
        return new StructuredEventDto("e1","t1","alice",action,"10.0.0.1",outcome,
                Instant.now(), riskScore, null, null);
    }

    private PolicyRuleDocument rule(String name, String condition, String severity) {
        PolicyRuleDocument r = new PolicyRuleDocument();
        r.setRuleId("r1"); r.setRuleName(name); r.setCondition(condition);
        r.setSeverity(severity); r.setActive(true);
        r.setComplianceFrameworks(List.of("GDPR","SOC2"));
        return r;
    }

    @Test
    void evaluate_conditionMatches_publishesViolation() {
        when(policyRuleRepository.findByTenantIdAndActiveTrue("t1"))
                .thenReturn(Flux.just(rule("DeleteRule","#action == 'DELETE_USER'","HIGH")));

        StepVerifier.create(service.evaluate(event("DELETE_USER","SUCCESS",30.0)))
                .verifyComplete();

        verify(kafkaTemplate).send(eq("policy-violations"), anyString(), any(PolicyViolationDto.class));
    }

    @Test
    void evaluate_conditionDoesNotMatch_noViolation() {
        when(policyRuleRepository.findByTenantIdAndActiveTrue("t1"))
                .thenReturn(Flux.just(rule("DeleteRule","#action == 'DELETE_USER'","HIGH")));

        StepVerifier.create(service.evaluate(event("LOGIN","SUCCESS",10.0)))
                .verifyComplete();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void evaluate_noRules_completesWithNoViolations() {
        when(policyRuleRepository.findByTenantIdAndActiveTrue("t1")).thenReturn(Flux.empty());

        StepVerifier.create(service.evaluate(event("DELETE_USER","SUCCESS",50.0)))
                .verifyComplete();

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void evaluate_riskScoreCondition_publishesWhenAboveThreshold() {
        when(policyRuleRepository.findByTenantIdAndActiveTrue("t1"))
                .thenReturn(Flux.just(rule("HighRisk","#riskScore > 70","CRITICAL")));

        StepVerifier.create(service.evaluate(event("LOGIN","SUCCESS",85.0)))
                .verifyComplete();

        verify(kafkaTemplate).send(anyString(), anyString(), argThat(v ->
                "CRITICAL".equals(v.severity()) && "r1".equals(v.ruleId())));
    }

    @Test
    void evaluate_malformedSpelExpression_doesNotThrow() {
        when(policyRuleRepository.findByTenantIdAndActiveTrue("t1"))
                .thenReturn(Flux.just(rule("BrokenRule","this is not valid SpEL !!!","MEDIUM")));

        StepVerifier.create(service.evaluate(event("LOGIN","SUCCESS",10.0)))
                .verifyComplete(); // should swallow the parse error

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void evaluate_multipleRules_publishesForEachMatch() {
        PolicyRuleDocument r1 = rule("Rule1","#outcome == 'FAILURE'","LOW");
        PolicyRuleDocument r2 = rule("Rule2","#action == 'LOGIN'","MEDIUM");
        r2.setRuleId("r2");
        when(policyRuleRepository.findByTenantIdAndActiveTrue("t1"))
                .thenReturn(Flux.just(r1, r2));

        StepVerifier.create(service.evaluate(event("LOGIN","FAILURE",10.0)))
                .verifyComplete();

        verify(kafkaTemplate, times(2)).send(anyString(), anyString(), any());
    }
}
