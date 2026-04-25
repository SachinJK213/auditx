package com.auditx.alert.consumer;

import com.auditx.alert.service.AlertService;
import com.auditx.common.constants.KafkaTopics;
import com.auditx.common.dto.AlertDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AlertConsumer {

    private static final Logger log = LoggerFactory.getLogger(AlertConsumer.class);

    private final AlertService alertService;

    public AlertConsumer(AlertService alertService) {
        this.alertService = alertService;
    }

    @KafkaListener(topics = KafkaTopics.ALERTS, groupId = "alert-service-group")
    public void consume(AlertDto alert) {
        alertService.process(alert)
                .doOnError(ex -> log.error(
                        "{\"event\":\"alert_consume_failed\",\"eventId\":\"{}\",\"topic\":\"{}\",\"consumerGroup\":\"alert-service-group\",\"attemptCount\":1,\"errorMessage\":\"{}\"}",
                        alert.eventId(), KafkaTopics.ALERTS, ex.getMessage()))
                .subscribe();
    }
}
