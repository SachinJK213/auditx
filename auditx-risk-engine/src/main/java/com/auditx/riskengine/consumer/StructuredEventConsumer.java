package com.auditx.riskengine.consumer;

import com.auditx.common.constants.KafkaTopics;
import com.auditx.common.dto.StructuredEventDto;
import com.auditx.riskengine.service.RiskEngineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class StructuredEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(StructuredEventConsumer.class);

    private final RiskEngineService riskEngineService;

    public StructuredEventConsumer(RiskEngineService riskEngineService) {
        this.riskEngineService = riskEngineService;
    }

    @KafkaListener(topics = KafkaTopics.STRUCTURED_EVENTS, groupId = "risk-engine-group")
    public void consume(StructuredEventDto event) {
        riskEngineService.process(event)
                .doOnError(ex -> log.error(
                        "{{\"eventId\":\"{}\",\"topic\":\"{}\",\"consumerGroup\":\"risk-engine-group\"," +
                        "\"attemptCount\":1,\"errorMessage\":\"{}\"}}",
                        event.eventId(), KafkaTopics.STRUCTURED_EVENTS, ex.getMessage()))
                .subscribe();
    }
}
