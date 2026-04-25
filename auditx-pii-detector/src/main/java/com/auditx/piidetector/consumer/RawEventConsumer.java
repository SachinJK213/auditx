package com.auditx.piidetector.consumer;

import com.auditx.common.constants.KafkaTopics;
import com.auditx.common.dto.PiiFindingDto;
import com.auditx.common.dto.RawEventDto;
import com.auditx.piidetector.model.PiiFindingDocument;
import com.auditx.piidetector.repository.PiiFindingRepository;
import com.auditx.piidetector.service.PiiScannerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Component
public class RawEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(RawEventConsumer.class);

    private final PiiScannerService scanner;
    private final PiiFindingRepository repository;
    private final KafkaTemplate<String, PiiFindingDto> kafkaTemplate;

    public RawEventConsumer(PiiScannerService scanner,
                            PiiFindingRepository repository,
                            KafkaTemplate<String, PiiFindingDto> kafkaTemplate) {
        this.scanner = scanner;
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = KafkaTopics.RAW_EVENTS, groupId = "pii-detector-group")
    public void consume(RawEventDto event) {
        List<PiiFindingDto.PiiMatch> matches = scanner.scan(event.payload());
        if (matches.isEmpty()) return;  // no PII found, skip

        PiiFindingDto dto = new PiiFindingDto(
            event.eventId(), event.tenantId(), matches, Instant.now()
        );

        // Save to MongoDB
        PiiFindingDocument doc = new PiiFindingDocument();
        doc.setEventId(event.eventId());
        doc.setTenantId(event.tenantId());
        doc.setMatches(matches);
        doc.setMatchCount(matches.size());
        doc.setScannedAt(Instant.now());

        repository.save(doc)
            .then(Mono.fromRunnable(() -> kafkaTemplate.send(KafkaTopics.PII_FINDINGS, event.tenantId(), dto)))
            .doOnSuccess(v -> log.info("PII finding stored and published: eventId={} tenantId={} matchCount={}",
                event.eventId(), event.tenantId(), matches.size()))
            .doOnError(e -> log.error("Failed to process PII finding for eventId={}", event.eventId(), e))
            .subscribe();
    }
}
