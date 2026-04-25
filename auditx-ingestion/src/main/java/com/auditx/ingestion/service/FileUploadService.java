package com.auditx.ingestion.service;

import com.auditx.common.dto.BatchIngestionResult;
import com.auditx.common.dto.RawEventDto;
import com.auditx.common.enums.PayloadType;
import com.auditx.common.util.IdempotencyKeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class FileUploadService {

    private static final Logger log = LoggerFactory.getLogger(FileUploadService.class);
    private final IngestionService ingestionService;

    public FileUploadService(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    public Mono<BatchIngestionResult> processUpload(FilePart filePart, String tenantId) {
        // 1. Collect all data buffers into one, then get bytes as String
        return DataBufferUtils.join(filePart.content())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return new String(bytes, StandardCharsets.UTF_8);
                })
                .flatMap(content -> {
                    // 2. Split by newlines
                    String[] lines = content.split("\\r?\\n");
                    List<String> payloads = new ArrayList<>();
                    for (String line : lines) {
                        String trimmed = line.trim();
                        // Skip empty lines, comments, and CSV header
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                        if (trimmed.toLowerCase().startsWith("timestamp,")) continue;
                        payloads.add(trimmed);
                    }

                    if (payloads.isEmpty()) {
                        return Mono.just(new BatchIngestionResult(0, 0, 0, List.of()));
                    }

                    // 3. Ingest each payload line
                    return Flux.fromIterable(payloads)
                            .flatMap(payload -> {
                                String eventId = IdempotencyKeyGenerator.generate();
                                PayloadType type = detectPayloadType(payload);
                                RawEventDto dto = new RawEventDto(
                                        eventId, tenantId, payload, type,
                                        "upload-" + eventId, Instant.now()
                                );
                                return ingestionService.ingest(dto, tenantId)
                                        .map(id -> Map.entry(true, id))
                                        .onErrorResume(ex -> {
                                            log.warn("Upload line failed: {}", ex.getMessage());
                                            return Mono.just(Map.entry(false, ""));
                                        });
                            })
                            .collectList()
                            .map(results -> {
                                List<String> eventIds = results.stream()
                                        .filter(Map.Entry::getKey)
                                        .map(Map.Entry::getValue)
                                        .filter(id -> !id.isEmpty())
                                        .toList();
                                int accepted = (int) results.stream().filter(Map.Entry::getKey).count();
                                return new BatchIngestionResult(results.size(), accepted,
                                        results.size() - accepted, eventIds);
                            });
                });
    }

    private PayloadType detectPayloadType(String line) {
        if (line.startsWith("{")) return PayloadType.JSON;
        if (line.startsWith("<") && line.matches("^<\\d+>.*")) return PayloadType.SYSLOG;
        // Try CSV: split and check if first field is ISO timestamp
        String[] fields = line.split(",", 2);
        if (fields.length >= 2) {
            try {
                Instant.parse(fields[0].trim());
                return PayloadType.CSV;
            } catch (Exception ignored) {
                // not CSV
            }
        }
        return PayloadType.RAW;
    }
}
