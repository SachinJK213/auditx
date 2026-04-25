package com.auditx.ingestion.service;

import com.auditx.common.dto.BatchIngestionResult;
import com.auditx.common.dto.RawEventDto;
import com.auditx.common.enums.PayloadType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileUploadServiceTest {

    @Mock
    private IngestionService ingestionService;

    @Mock
    private FilePart filePart;

    private FileUploadService service;

    @BeforeEach
    void setUp() {
        service = new FileUploadService(ingestionService);
    }

    private DataBuffer bufferOf(String content) {
        return new DefaultDataBufferFactory().wrap(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void processUpload_validJsonLine_acceptsOne() {
        String content = "{\"userId\":\"alice\",\"action\":\"LOGIN\",\"tenantId\":\"tenant-demo\",\"outcome\":\"SUCCESS\"}";
        when(filePart.content()).thenReturn(Flux.just(bufferOf(content)));
        when(ingestionService.ingest(any(), anyString())).thenReturn(Mono.just("event-123"));

        StepVerifier.create(service.processUpload(filePart, "tenant-demo"))
                .assertNext(result -> {
                    assertThat(result.accepted()).isEqualTo(1);
                    assertThat(result.failed()).isEqualTo(0);
                    assertThat(result.total()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    void processUpload_skipsCsvHeaderAndEmptyLines() {
        String content = "timestamp,userId,action,sourceIp,tenantId,outcome\n\n2024-01-01T00:00:00Z,alice,LOGIN,10.0.0.1,tenant-demo,SUCCESS";
        when(filePart.content()).thenReturn(Flux.just(bufferOf(content)));
        when(ingestionService.ingest(any(), anyString())).thenReturn(Mono.just("event-456"));

        StepVerifier.create(service.processUpload(filePart, "tenant-demo"))
                .assertNext(result -> {
                    assertThat(result.total()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    void processUpload_emptyFile_returnsZeroCounts() {
        when(filePart.content()).thenReturn(Flux.just(bufferOf("")));

        StepVerifier.create(service.processUpload(filePart, "tenant-demo"))
                .assertNext(result -> {
                    assertThat(result.total()).isEqualTo(0);
                    assertThat(result.accepted()).isEqualTo(0);
                    assertThat(result.failed()).isEqualTo(0);
                })
                .verifyComplete();

        verify(ingestionService, never()).ingest(any(), anyString());
    }

    @Test
    void processUpload_oneLineFails_countedAsFailed() {
        String content = "{\"userId\":\"alice\"}";
        when(filePart.content()).thenReturn(Flux.just(bufferOf(content)));
        when(ingestionService.ingest(any(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("kafka down")));

        StepVerifier.create(service.processUpload(filePart, "tenant-demo"))
                .assertNext(result -> {
                    assertThat(result.total()).isEqualTo(1);
                    assertThat(result.failed()).isEqualTo(1);
                    assertThat(result.accepted()).isEqualTo(0);
                })
                .verifyComplete();
    }

    @Test
    void processUpload_detectsJsonFormat() {
        String content = "{\"userId\":\"alice\"}";
        when(filePart.content()).thenReturn(Flux.just(bufferOf(content)));
        when(ingestionService.ingest(any(), anyString())).thenReturn(Mono.just("event-789"));

        ArgumentCaptor<RawEventDto> captor = ArgumentCaptor.forClass(RawEventDto.class);

        StepVerifier.create(service.processUpload(filePart, "tenant-demo"))
                .assertNext(result -> assertThat(result.total()).isEqualTo(1))
                .verifyComplete();

        verify(ingestionService).ingest(captor.capture(), anyString());
        assertThat(captor.getValue().payloadType()).isEqualTo(PayloadType.JSON);
    }
}
