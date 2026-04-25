package com.auditx.riskengine.service;

import com.auditx.common.dto.RiskScoreDto;
import com.auditx.common.dto.StructuredEventDto;
import com.auditx.riskengine.model.AuditEventDocument;
import com.auditx.riskengine.model.RiskRuleDocument;
import com.auditx.riskengine.repository.AuditEventRepository;
import com.auditx.riskengine.repository.RiskRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskScoringEngineTest {

    @Mock RiskRuleRepository riskRuleRepository;
    @Mock AuditEventRepository auditEventRepository;

    private RiskScoringEngine engine;

    @BeforeEach
    void setUp() {
        engine = new RiskScoringEngine(riskRuleRepository, auditEventRepository);
    }

    private StructuredEventDto event(String userId, String action, String outcome, String ip) {
        return new StructuredEventDto("evt-1", "t1", userId, action, ip, outcome,
                Instant.now(), null, null, null);
    }

    private RiskRuleDocument rule(String type, String name, double weight, Integer threshold, Integer window) {
        RiskRuleDocument r = new RiskRuleDocument();
        r.setRuleType(type); r.setRuleName(name); r.setWeight(weight);
        r.setThreshold(threshold); r.setWindowMinutes(window); r.setActive(true);
        return r;
    }

    @Test
    void computeScore_noRules_returnsZero() {
        when(riskRuleRepository.findByTenantIdAndActiveTrue("t1")).thenReturn(Flux.empty());

        StepVerifier.create(engine.computeScore(event("alice", "LOGIN", "SUCCESS", "10.0.0.1")))
                .assertNext(dto -> {
                    assertThat(dto.score()).isEqualTo(0.0);
                    assertThat(dto.ruleMatches()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void computeScore_failedLoginThreshold_notMet_returnsZero() {
        when(riskRuleRepository.findByTenantIdAndActiveTrue("t1"))
                .thenReturn(Flux.just(rule("FAILED_LOGIN_THRESHOLD", "FailedLogin", 50.0, 3, 60)));
        when(auditEventRepository.findByTenantIdAndUserIdAndOutcomeAndTimestampAfter(
                eq("t1"), eq("alice"), eq("FAILURE"), any()))
                .thenReturn(Flux.just(new AuditEventDocument(), new AuditEventDocument())); // 2 < 3

        StepVerifier.create(engine.computeScore(event("alice", "LOGIN", "FAILURE", "10.0.0.1")))
                .assertNext(dto -> assertThat(dto.score()).isEqualTo(0.0))
                .verifyComplete();
    }

    @Test
    void computeScore_failedLoginThreshold_met_addsWeight() {
        when(riskRuleRepository.findByTenantIdAndActiveTrue("t1"))
                .thenReturn(Flux.just(rule("FAILED_LOGIN_THRESHOLD", "FailedLogin", 50.0, 3, 60)));
        // 3 failures — exactly at threshold
        when(auditEventRepository.findByTenantIdAndUserIdAndOutcomeAndTimestampAfter(
                eq("t1"), eq("alice"), eq("FAILURE"), any()))
                .thenReturn(Flux.just(new AuditEventDocument(), new AuditEventDocument(), new AuditEventDocument()));

        StepVerifier.create(engine.computeScore(event("alice", "LOGIN", "FAILURE", "10.0.0.1")))
                .assertNext(dto -> {
                    assertThat(dto.score()).isEqualTo(50.0);
                    assertThat(dto.ruleMatches()).containsExactly("FailedLogin");
                })
                .verifyComplete();
    }

    @Test
    void computeScore_multipleRulesMatch_sumIsClampedAt100() {
        RiskRuleDocument r1 = rule("FAILED_LOGIN_THRESHOLD", "Rule1", 70.0, 1, 60);
        RiskRuleDocument r2 = rule("FAILED_LOGIN_THRESHOLD", "Rule2", 70.0, 1, 60);
        when(riskRuleRepository.findByTenantIdAndActiveTrue("t1")).thenReturn(Flux.just(r1, r2));
        when(auditEventRepository.findByTenantIdAndUserIdAndOutcomeAndTimestampAfter(
                any(), any(), any(), any()))
                .thenReturn(Flux.just(new AuditEventDocument()));

        StepVerifier.create(engine.computeScore(event("alice", "LOGIN", "FAILURE", "10.0.0.1")))
                .assertNext(dto -> assertThat(dto.score()).isEqualTo(100.0))
                .verifyComplete();
    }

    @Test
    void computeScore_geoAnomaly_newRegion_addsWeight() {
        when(riskRuleRepository.findByTenantIdAndActiveTrue("t1"))
                .thenReturn(Flux.just(rule("GEO_ANOMALY", "GeoAnomaly", 40.0, null, null)));
        // Historical events from region-10 (10.x.x.x)
        AuditEventDocument historical = new AuditEventDocument();
        historical.setSourceIp("10.0.0.1");
        when(auditEventRepository.findByTenantIdAndUserIdAndTimestampAfter(eq("t1"), eq("alice"), any()))
                .thenReturn(Flux.just(historical));

        // Current event from region-203 — new region
        StepVerifier.create(engine.computeScore(event("alice", "LOGIN", "SUCCESS", "203.0.113.1")))
                .assertNext(dto -> {
                    assertThat(dto.score()).isEqualTo(40.0);
                    assertThat(dto.ruleMatches()).containsExactly("GeoAnomaly");
                })
                .verifyComplete();
    }

    @Test
    void computeScore_geoAnomaly_knownRegion_noScore() {
        when(riskRuleRepository.findByTenantIdAndActiveTrue("t1"))
                .thenReturn(Flux.just(rule("GEO_ANOMALY", "GeoAnomaly", 40.0, null, null)));
        AuditEventDocument historical = new AuditEventDocument();
        historical.setSourceIp("192.168.1.50");
        when(auditEventRepository.findByTenantIdAndUserIdAndTimestampAfter(eq("t1"), eq("alice"), any()))
                .thenReturn(Flux.just(historical));

        // Same region-192
        StepVerifier.create(engine.computeScore(event("alice", "LOGIN", "SUCCESS", "192.168.1.99")))
                .assertNext(dto -> assertThat(dto.score()).isEqualTo(0.0))
                .verifyComplete();
    }

    @Test
    void computeScore_customRule_alwaysAddsWeight() {
        when(riskRuleRepository.findByTenantIdAndActiveTrue("t1"))
                .thenReturn(Flux.just(rule("CUSTOM", "CustomRule", 20.0, null, null)));

        StepVerifier.create(engine.computeScore(event("alice", "DELETE_USER", "SUCCESS", "10.0.0.1")))
                .assertNext(dto -> {
                    assertThat(dto.score()).isEqualTo(20.0);
                    assertThat(dto.ruleMatches()).containsExactly("CustomRule");
                })
                .verifyComplete();
    }

    @Test
    void computeScore_unknownRuleType_contributesZero() {
        when(riskRuleRepository.findByTenantIdAndActiveTrue("t1"))
                .thenReturn(Flux.just(rule("UNKNOWN_TYPE", "Unknown", 99.0, null, null)));

        StepVerifier.create(engine.computeScore(event("alice", "LOGIN", "SUCCESS", "10.0.0.1")))
                .assertNext(dto -> assertThat(dto.score()).isEqualTo(0.0))
                .verifyComplete();
    }
}
