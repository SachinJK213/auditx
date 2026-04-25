package com.auditx.ingestion.service;

import com.auditx.common.dto.RawEventDto;
import com.auditx.common.util.IdempotencyKeyGenerator;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class IngestionService {

    private final IdempotencyService idempotencyService;
    private final KafkaEventPublisher kafkaEventPublisher;

    public IngestionService(IdempotencyService idempotencyService,
                            KafkaEventPublisher kafkaEventPublisher) {
        this.idempotencyService = idempotencyService;
        this.kafkaEventPublisher = kafkaEventPublisher;
    }

    /**
     * Ingests a raw event for the given tenant.
     * <ol>
     *   <li>Assigns tenantId and generates eventId if absent.</li>
     *   <li>Checks idempotency — returns existing eventId if duplicate.</li>
     *   <li>Publishes to Kafka.</li>
     *   <li>Stores idempotency key in Redis.</li>
     * </ol>
     *
     * @return Mono emitting the eventId
     */
    public Mono<String> ingest(RawEventDto dto, String tenantId) {
        // Ensure eventId and idempotencyKey are set
        String eventId = (dto.eventId() != null && !dto.eventId().isBlank())
                ? dto.eventId()
                : IdempotencyKeyGenerator.generate();

        String idempotencyKey = (dto.idempotencyKey() != null && !dto.idempotencyKey().isBlank())
                ? dto.idempotencyKey()
                : eventId;

        RawEventDto enriched = new RawEventDto(
                eventId,
                tenantId,
                dto.payload(),
                dto.payloadType(),
                idempotencyKey,
                dto.timestamp() != null ? dto.timestamp() : java.time.Instant.now()
        );

        return idempotencyService.checkDuplicate(idempotencyKey, tenantId)
                .flatMap(existing -> {
                    if (existing.isPresent()) {
                        return Mono.just(existing.get());
                    }
                    return kafkaEventPublisher.publish(enriched)
                            .flatMap(publishedEventId ->
                                    idempotencyService.store(idempotencyKey, tenantId, publishedEventId)
                                            .thenReturn(publishedEventId)
                            );
                });
    }
}
