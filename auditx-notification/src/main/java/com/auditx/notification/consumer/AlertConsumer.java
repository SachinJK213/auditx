package com.auditx.notification.consumer;

import com.auditx.common.constants.KafkaTopics;
import com.auditx.common.dto.AlertDto;
import com.auditx.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AlertConsumer {

    private static final Logger log = LoggerFactory.getLogger(AlertConsumer.class);

    private final NotificationService notificationService;

    public AlertConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = KafkaTopics.ALERTS, groupId = "notification-group")
    public void consume(AlertDto alert) {
        notificationService.processAlert(alert)
                .doOnError(ex -> log.error(
                        "{\"event\":\"notification_consume_failed\",\"alertId\":\"{}\",\"tenantId\":\"{}\",\"errorMessage\":\"{}\"}",
                        alert.alertId(), alert.tenantId(), ex.getMessage()))
                .subscribe();
    }
}
