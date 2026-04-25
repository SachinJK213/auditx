package com.auditx.enrichment.consumer;

import com.auditx.common.constants.KafkaTopics;
import com.auditx.common.dto.StructuredEventDto;
import com.auditx.enrichment.service.EnrichmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class StructuredEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(StructuredEventConsumer.class);

    private final EnrichmentService enrichmentService;

    public StructuredEventConsumer(EnrichmentService enrichmentService) {
        this.enrichmentService = enrichmentService;
    }

    @KafkaListener(topics = KafkaTopics.STRUCTURED_EVENTS, groupId = "enrichment-group")
    public void consume(StructuredEventDto event) {
        enrichmentService.enrich(event)
                .doOnError(ex -> log.error("{\"eventId\":\"{}\",\"error\":\"enrichment failed: {}\"}",
                        event.eventId(), ex.getMessage()))
                .subscribe();
    }
}
