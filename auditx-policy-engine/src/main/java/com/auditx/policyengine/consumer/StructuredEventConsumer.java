package com.auditx.policyengine.consumer;

import com.auditx.common.constants.KafkaTopics;
import com.auditx.common.dto.StructuredEventDto;
import com.auditx.policyengine.service.PolicyEngineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class StructuredEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(StructuredEventConsumer.class);

    private final PolicyEngineService policyEngineService;

    public StructuredEventConsumer(PolicyEngineService policyEngineService) {
        this.policyEngineService = policyEngineService;
    }

    @KafkaListener(topics = KafkaTopics.STRUCTURED_EVENTS, groupId = "policy-engine-group")
    public void consume(StructuredEventDto event) {
        policyEngineService.evaluate(event)
                .doOnError(ex -> log.error("{\"eventId\":\"{}\",\"error\":\"policy evaluation failed: {}\"}",
                        event.eventId(), ex.getMessage()))
                .subscribe();
    }
}
