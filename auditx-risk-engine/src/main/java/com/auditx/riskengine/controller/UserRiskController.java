package com.auditx.riskengine.controller;

import com.auditx.riskengine.model.UserRiskProfileDocument;
import com.auditx.riskengine.repository.UserRiskProfileRepository;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/user-risk")
public class UserRiskController {

    private final UserRiskProfileRepository userRiskProfileRepository;

    public UserRiskController(UserRiskProfileRepository userRiskProfileRepository) {
        this.userRiskProfileRepository = userRiskProfileRepository;
    }

    @GetMapping
    public Flux<UserRiskProfileDocument> listTopRisk(@RequestHeader("X-Tenant-Id") String tenantId) {
        return userRiskProfileRepository.findByTenantIdOrderByCumulativeScoreDesc(tenantId);
    }

    @GetMapping("/{userId}")
    public Mono<UserRiskProfileDocument> getProfile(@RequestHeader("X-Tenant-Id") String tenantId,
                                                    @PathVariable String userId) {
        return userRiskProfileRepository.findByTenantIdAndUserId(tenantId, userId);
    }
}
