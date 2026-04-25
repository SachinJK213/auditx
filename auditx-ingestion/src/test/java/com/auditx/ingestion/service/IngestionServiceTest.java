package com.auditx.ingestion.service;

import com.auditx.common.dto.RawEventDto;
import com.auditx.common.enums.PayloadType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock IdempotencyService idempotencyService;
    @Mock KafkaEventPublisher kafkaEventPublisher;

    private IngestionService service;

    @BeforeEach
    void setUp() {
        service = new IngestionService(idempotencyService, kafkaEventPublisher);
    }

    private RawEventDto dto(String eventId, String idemKey) {
        return new RawEventDto(eventId, null, "payload", PayloadType.RAW, idemKey, Instant.now());
    }

    @Test
    void ingest_newEvent_publishesAndStoresIdempotencyKey() {
        when(idempotencyService.checkDuplicate(anyString(), anyString()))
                .thenReturn(Mono.just(Optional.empty()));
        when(kafkaEventPublisher.publish(any())).thenReturn(Mono.just("evt-generated"));
        when(idempotencyService.store(anyString(), anyString(), anyString())).thenReturn(Mono.empty());

        StepVerifier.create(service.ingest(dto(null, "key-1"), "t1"))
                .assertNext(id -> assertThat(id).isNotBlank())
                .verifyComplete();

        verify(kafkaEventPublisher).publish(any());
        verify(idempotencyService).store(anyString(), eq("t1"), anyString());
    }

    @Test
    void ingest_duplicateKey_returnsExistingEventIdWithoutPublishing() {
        when(idempotencyService.checkDuplicate(eq("key-dup"), eq("t1")))
                .thenReturn(Mono.just(Optional.of("existing-event-id")));

        StepVerifier.create(service.ingest(dto(null, "key-dup"), "t1"))
                .assertNext(id -> assertThat(id).isEqualTo("existing-event-id"))
                .verifyComplete();

        verify(kafkaEventPublisher, never()).publish(any());
        verify(idempotencyService, never()).store(any(), any(), any());
    }

    @Test
    void ingest_providedEventIdIsPreserved() {
        when(idempotencyService.checkDuplicate(anyString(), anyString()))
                .thenReturn(Mono.just(Optional.empty()));
        when(kafkaEventPublisher.publish(argThat(e -> "my-event-id".equals(e.eventId()))))
                .thenReturn(Mono.just("my-event-id"));
        when(idempotencyService.store(any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(service.ingest(dto("my-event-id", "k"), "t1"))
                .assertNext(id -> assertThat(id).isEqualTo("my-event-id"))
                .verifyComplete();
    }

    @Test
    void ingest_nullIdempotencyKey_usesGeneratedEventIdAsKey() {
        when(idempotencyService.checkDuplicate(anyString(), eq("t1")))
                .thenReturn(Mono.just(Optional.empty()));
        when(kafkaEventPublisher.publish(any())).thenReturn(Mono.just("gen-id"));
        when(idempotencyService.store(any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(service.ingest(dto(null, null), "t1"))
                .assertNext(id -> assertThat(id).isNotBlank())
                .verifyComplete();
    }
}
