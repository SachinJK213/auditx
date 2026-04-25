package com.auditx.livestream.consumer;

import com.auditx.common.dto.StructuredEventDto;
import com.auditx.livestream.dto.LiveEventDto;
import com.auditx.livestream.service.EventBroadcastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class StructuredEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(StructuredEventConsumer.class);

    private final EventBroadcastService broadcastService;

    public StructuredEventConsumer(EventBroadcastService broadcastService) {
        this.broadcastService = broadcastService;
    }

    @KafkaListener(
            topics = "structured-events",
            groupId = "live-stream-consumer",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(StructuredEventDto event) {
        try {
            LiveEventDto live = new LiveEventDto(
                    event.eventId(),
                    event.tenantId(),
                    event.userId(),
                    event.action(),
                    event.sourceIp(),
                    event.outcome(),
                    event.timestamp(),
                    event.riskScore(),
                    LiveEventDto.toRiskLevel(event.riskScore()),
                    event.ruleMatches(),
                    "PROCESSED",
                    Instant.now()
            );
            broadcastService.publish(live);
        } catch (Exception e) {
            log.error("Failed to broadcast event eventId={}", event.eventId(), e);
        }
    }
}
