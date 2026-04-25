package com.auditx.riskengine.service;

import com.auditx.common.dto.StructuredEventDto;
import com.auditx.riskengine.model.UserRiskProfileDocument;
import com.auditx.riskengine.repository.UserRiskProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
public class UserRiskAggregationService {

    private static final Logger log = LoggerFactory.getLogger(UserRiskAggregationService.class);
    private static final double HIGH_RISK_THRESHOLD = 70.0;

    private final UserRiskProfileRepository userRiskProfileRepository;

    public UserRiskAggregationService(UserRiskProfileRepository userRiskProfileRepository) {
        this.userRiskProfileRepository = userRiskProfileRepository;
    }

    public Mono<Void> aggregate(StructuredEventDto event, double riskScore) {
        if (event.userId() == null || event.userId().isBlank()) {
            return Mono.empty();
        }
        return userRiskProfileRepository.findByTenantIdAndUserId(event.tenantId(), event.userId())
                .flatMap(profile -> updateExisting(profile, riskScore))
                .switchIfEmpty(Mono.defer(() -> createNew(event, riskScore)))
                .doOnSuccess(p -> log.info(
                        "{\"userId\":\"{}\",\"tenantId\":\"{}\",\"cumulativeScore\":\"{}\",\"eventCount\":\"{}\"}",
                        p.getUserId(), p.getTenantId(), p.getCumulativeScore(), p.getEventCount()))
                .then();
    }

    private Mono<UserRiskProfileDocument> updateExisting(UserRiskProfileDocument profile, double riskScore) {
        int newCount = profile.getEventCount() + 1;
        // Weighted moving average: (oldAvg * oldCount + newScore) / newCount
        double newAvg = (profile.getCumulativeScore() * profile.getEventCount() + riskScore) / newCount;
        profile.setCumulativeScore(Math.min(100.0, newAvg));
        profile.setEventCount(newCount);
        if (riskScore > HIGH_RISK_THRESHOLD) {
            profile.setHighRiskEventCount(profile.getHighRiskEventCount() + 1);
        }
        profile.setLastUpdated(Instant.now());
        return userRiskProfileRepository.save(profile);
    }

    private Mono<UserRiskProfileDocument> createNew(StructuredEventDto event, double riskScore) {
        UserRiskProfileDocument profile = new UserRiskProfileDocument();
        profile.setTenantId(event.tenantId());
        profile.setUserId(event.userId());
        profile.setCumulativeScore(riskScore);
        profile.setEventCount(1);
        profile.setHighRiskEventCount(riskScore > HIGH_RISK_THRESHOLD ? 1 : 0);
        profile.setLastUpdated(Instant.now());
        return userRiskProfileRepository.save(profile);
    }
}
