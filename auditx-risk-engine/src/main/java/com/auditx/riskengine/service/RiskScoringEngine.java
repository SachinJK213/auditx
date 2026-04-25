package com.auditx.riskengine.service;

import com.auditx.common.dto.RiskScoreDto;
import com.auditx.common.dto.StructuredEventDto;
import com.auditx.riskengine.repository.AuditEventRepository;
import com.auditx.riskengine.repository.RiskRuleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RiskScoringEngine {

    private final RiskRuleRepository riskRuleRepository;
    private final AuditEventRepository auditEventRepository;

    @Value("${auditx.risk.failed-login-window-minutes:60}")
    private int failedLoginWindowMinutes;

    @Value("${auditx.risk.failed-login-threshold:5}")
    private int failedLoginThreshold;

    @Value("${auditx.risk.geo-anomaly-lookback-days:30}")
    private int geoAnomalyLookbackDays;

    public RiskScoringEngine(RiskRuleRepository riskRuleRepository,
                             AuditEventRepository auditEventRepository) {
        this.riskRuleRepository = riskRuleRepository;
        this.auditEventRepository = auditEventRepository;
    }

    public Mono<RiskScoreDto> computeScore(StructuredEventDto event) {
        return riskRuleRepository.findByTenantIdAndActiveTrue(event.tenantId())
                .collectList()
                .flatMap(rules -> {
                    if (rules.isEmpty()) {
                        return Mono.just(new RiskScoreDto(
                                event.eventId(), event.tenantId(), 0.0, List.of(), Instant.now()));
                    }

                    List<Mono<RuleResult>> evaluations = rules.stream()
                            .map(rule -> switch (rule.getRuleType()) {
                                case "FAILED_LOGIN_THRESHOLD" -> evaluateFailedLoginThreshold(event, rule);
                                case "GEO_ANOMALY" -> evaluateGeoAnomaly(event, rule);
                                case "CUSTOM" -> Mono.just(new RuleResult(rule.getRuleName(), rule.getWeight()));
                                default -> Mono.just(new RuleResult(null, 0.0));
                            })
                            .toList();

                    return Mono.zip(evaluations, results -> {
                        double totalScore = 0.0;
                        List<String> ruleMatches = new ArrayList<>();
                        for (Object obj : results) {
                            RuleResult r = (RuleResult) obj;
                            if (r.matched()) {
                                totalScore += r.weight();
                                ruleMatches.add(r.ruleName());
                            }
                        }
                        double clamped = Math.min(100.0, Math.max(0.0, totalScore));
                        return new RiskScoreDto(event.eventId(), event.tenantId(), clamped, ruleMatches, Instant.now());
                    });
                });
    }

    private Mono<RuleResult> evaluateFailedLoginThreshold(StructuredEventDto event, com.auditx.riskengine.model.RiskRuleDocument rule) {
        int windowMins = rule.getWindowMinutes() != null ? rule.getWindowMinutes() : failedLoginWindowMinutes;
        int threshold = rule.getThreshold() != null ? rule.getThreshold() : failedLoginThreshold;
        Instant windowStart = Instant.now().minus(windowMins, ChronoUnit.MINUTES);

        return auditEventRepository.findByTenantIdAndUserIdAndOutcomeAndTimestampAfter(
                        event.tenantId(), event.userId(), "FAILURE", windowStart)
                .count()
                .map(count -> {
                    if (count >= threshold) {
                        return new RuleResult(rule.getRuleName(), rule.getWeight());
                    }
                    return new RuleResult(null, 0.0);
                });
    }

    private Mono<RuleResult> evaluateGeoAnomaly(StructuredEventDto event, com.auditx.riskengine.model.RiskRuleDocument rule) {
        Instant lookbackStart = Instant.now().minus(geoAnomalyLookbackDays, ChronoUnit.DAYS);
        String currentRegion = extractRegion(event.sourceIp());

        return auditEventRepository.findByTenantIdAndUserIdAndTimestampAfter(
                        event.tenantId(), event.userId(), lookbackStart)
                .map(doc -> extractRegion(doc.getSourceIp()))
                .collect(Collectors.toSet())
                .map((Set<String> historicalRegions) -> {
                    if (!historicalRegions.isEmpty() && !historicalRegions.contains(currentRegion)) {
                        return new RuleResult(rule.getRuleName(), rule.getWeight());
                    }
                    return new RuleResult(null, 0.0);
                });
    }

    /**
     * Simple IP-to-region stub: uses the first octet to determine region.
     * e.g. 192.168.1.1 → "region-192"
     */
    private String extractRegion(String sourceIp) {
        if (sourceIp == null || sourceIp.isBlank()) {
            return "region-unknown";
        }
        int dotIndex = sourceIp.indexOf('.');
        String firstOctet = dotIndex > 0 ? sourceIp.substring(0, dotIndex) : sourceIp;
        return "region-" + firstOctet;
    }

    private record RuleResult(String ruleName, double weight) {
        boolean matched() {
            return ruleName != null;
        }
    }
}
