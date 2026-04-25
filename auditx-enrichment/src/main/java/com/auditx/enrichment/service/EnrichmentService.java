package com.auditx.enrichment.service;

import com.auditx.common.constants.KafkaTopics;
import com.auditx.common.dto.EnrichedEventDto;
import com.auditx.common.dto.StructuredEventDto;
import com.auditx.enrichment.model.EnrichedEventDocument;
import com.auditx.enrichment.repository.EnrichedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
public class EnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentService.class);

    private final GeoIpService geoIpService;
    private final EnrichedEventRepository repository;
    private final KafkaTemplate<String, EnrichedEventDto> kafkaTemplate;

    public EnrichmentService(GeoIpService geoIpService,
                             EnrichedEventRepository repository,
                             KafkaTemplate<String, EnrichedEventDto> kafkaTemplate) {
        this.geoIpService = geoIpService;
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    public Mono<Void> enrich(StructuredEventDto event) {
        return geoIpService.lookup(event.sourceIp())
                .flatMap(geo -> {
                    EnrichedEventDto dto = new EnrichedEventDto(
                            event.eventId(), event.tenantId(), event.userId(),
                            event.action(), event.sourceIp(), event.outcome(),
                            event.timestamp(), event.riskScore(),
                            geo.country(), geo.countryCode(), geo.city(),
                            geo.lat(), geo.lon(), geo.isp(), geo.privateIp(),
                            Instant.now()
                    );

                    EnrichedEventDocument doc = toDocument(dto);
                    return repository.save(doc)
                            .doOnSuccess(saved -> log.info(
                                    "{\"eventId\":\"{}\",\"tenantId\":\"{}\",\"country\":\"{}\",\"privateIp\":{}}",
                                    saved.getEventId(), saved.getTenantId(), saved.getCountry(), saved.isPrivateIp()))
                            .then(Mono.fromRunnable(() ->
                                    kafkaTemplate.send(KafkaTopics.ENRICHED_EVENTS, event.tenantId(), dto)))
                            .then();
                });
    }

    private EnrichedEventDocument toDocument(EnrichedEventDto dto) {
        EnrichedEventDocument doc = new EnrichedEventDocument();
        doc.setEventId(dto.eventId());
        doc.setTenantId(dto.tenantId());
        doc.setUserId(dto.userId());
        doc.setAction(dto.action());
        doc.setSourceIp(dto.sourceIp());
        doc.setOutcome(dto.outcome());
        doc.setTimestamp(dto.timestamp());
        doc.setRiskScore(dto.riskScore());
        doc.setCountry(dto.country());
        doc.setCountryCode(dto.countryCode());
        doc.setCity(dto.city());
        doc.setLat(dto.lat());
        doc.setLon(dto.lon());
        doc.setIsp(dto.isp());
        doc.setPrivateIp(dto.privateIp());
        doc.setEnrichedAt(dto.enrichedAt());
        return doc;
    }
}
