package com.auditx.parser.service;

import com.auditx.common.dto.RawEventDto;
import com.auditx.common.dto.StructuredEventDto;
import com.auditx.common.enums.PayloadType;
import com.auditx.parser.model.AuditEventDocument;
import com.auditx.parser.model.RawLogDocument;
import com.auditx.parser.repository.AuditEventRepository;
import com.auditx.parser.repository.RawLogRepository;
import com.auditx.parser.strategy.ParsingStrategy;
import com.auditx.parser.strategy.ParsingStrategyRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.core.KafkaTemplate;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParserServiceTest {

    @Mock ParsingStrategyRegistry registry;
    @Mock AuditEventRepository auditEventRepository;
    @Mock RawLogRepository rawLogRepository;
    @Mock KafkaTemplate<String, StructuredEventDto> kafkaTemplate;
    @Mock ReactiveMongoTemplate reactiveMongoTemplate;

    private ParserService service;

    @BeforeEach
    void setUp() {
        service = new ParserService(registry, auditEventRepository, rawLogRepository,
                kafkaTemplate, reactiveMongoTemplate);
    }

    @Test
    void process_matchingStrategy_upsertsThenPublishesToKafka() {
        StructuredEventDto parsed = new StructuredEventDto(
                "evt-1", "t1", "alice", "LOGIN", "192.168.1.1", "SUCCESS",
                Instant.now(), null, null, null);
        ParsingStrategy strategy = mock(ParsingStrategy.class);
        when(strategy.parse(anyString())).thenReturn(parsed);
        when(registry.find(anyString())).thenReturn(Optional.of(strategy));

        // upsert returns an UpdateResult stub
        when(reactiveMongoTemplate.upsert(any(Query.class), any(Update.class), eq(AuditEventDocument.class)))
                .thenReturn(Mono.just(com.mongodb.client.result.UpdateResult.acknowledged(0, 1L, null)));

        RawEventDto raw = new RawEventDto("evt-1", "t1", "timestamp=2024-06-01T10:00:00Z userId=alice " +
                "action=LOGIN sourceIp=192.168.1.1 tenantId=t1 outcome=SUCCESS",
                PayloadType.RAW, "key-1", Instant.now());

        StepVerifier.create(service.process(raw))
                .verifyComplete();

        verify(kafkaTemplate).send(eq("structured-events"), eq("evt-1"), any(StructuredEventDto.class));
    }

    @Test
    void process_noMatchingStrategy_savesToRawLogs() {
        when(registry.find(anyString())).thenReturn(Optional.empty());
        when(rawLogRepository.save(any(RawLogDocument.class)))
                .thenReturn(Mono.just(new RawLogDocument()));

        RawEventDto raw = new RawEventDto("evt-2", "t1", "unrecognised log format @@##",
                PayloadType.RAW, "key-2", Instant.now());

        StepVerifier.create(service.process(raw))
                .verifyComplete();

        verify(rawLogRepository).save(argThat(doc ->
                "FAILED".equals(doc.getParseStatus()) && "t1".equals(doc.getTenantId())));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void process_incomingEventIdPreserved() {
        StructuredEventDto parsed = new StructuredEventDto(
                "generated-id", "t1", "alice", "LOGIN", "192.168.1.1", "SUCCESS",
                Instant.now(), null, null, null);
        ParsingStrategy strategy = mock(ParsingStrategy.class);
        when(strategy.parse(anyString())).thenReturn(parsed);
        when(registry.find(anyString())).thenReturn(Optional.of(strategy));
        when(reactiveMongoTemplate.upsert(any(), any(), eq(AuditEventDocument.class)))
                .thenReturn(Mono.just(com.mongodb.client.result.UpdateResult.acknowledged(0, 1L, null)));

        RawEventDto raw = new RawEventDto("incoming-event-id", "t1", "p", PayloadType.RAW, "k", Instant.now());

        StepVerifier.create(service.process(raw)).verifyComplete();

        // Should send with the INCOMING id, not the generated one
        verify(kafkaTemplate).send(anyString(), eq("incoming-event-id"), any());
    }
}
