package com.auditx.compliance.consumer;

import com.auditx.common.constants.KafkaTopics;
import com.auditx.common.dto.PolicyViolationDto;
import com.auditx.compliance.service.ComplianceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PolicyViolationConsumer {

    private static final Logger log = LoggerFactory.getLogger(PolicyViolationConsumer.class);

    private final ComplianceService complianceService;

    public PolicyViolationConsumer(ComplianceService complianceService) {
        this.complianceService = complianceService;
    }

    @KafkaListener(topics = KafkaTopics.POLICY_VIOLATIONS, groupId = "compliance-service-group")
    public void consume(PolicyViolationDto violation) {
        complianceService.recordViolation(violation)
                .doOnError(ex -> log.error("{\"violationId\":\"{}\",\"error\":\"compliance recording failed: {}\"}",
                        violation.violationId(), ex.getMessage()))
                .subscribe();
    }
}
