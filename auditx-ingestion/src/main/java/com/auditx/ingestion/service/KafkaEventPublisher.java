package com.auditx.ingestion.service;

import com.auditx.common.constants.KafkaTopics;
import com.auditx.common.dto.RawEventDto;
import com.auditx.common.exception.KafkaPublishException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;

@Service
public class KafkaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);
    private static final Duration PUBLISH_TIMEOUT = Duration.ofMillis(500);

    private final KafkaTemplate<String, RawEventDto> kafkaTemplate;

    public KafkaEventPublisher(KafkaTemplate<String, RawEventDto> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes the event to the raw-events Kafka topic.
     * Times out after 500ms; throws KafkaPublishException on timeout or error.
     *
     * @return Mono emitting the eventId on success
     */
    public Mono<String> publish(RawEventDto dto) {
        return Mono.fromFuture(() -> kafkaTemplate.send(KafkaTopics.RAW_EVENTS, dto.tenantId(), dto)
                        .toCompletableFuture())
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(PUBLISH_TIMEOUT)
                .map(result -> dto.eventId())
                .onErrorMap(ex -> {
                    log.error("{{\"tenantId\":\"{}\",\"timestamp\":\"{}\",\"error\":\"{}\"}}",
                            dto.tenantId(), Instant.now(), ex.getMessage());
                    if (ex instanceof KafkaPublishException) {
                        return ex;
                    }
                    return new KafkaPublishException("Failed to publish event to Kafka", ex);
                });
    }
}
