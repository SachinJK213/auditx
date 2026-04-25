package com.auditx.ingestion.controller;

import com.auditx.common.dto.BatchIngestionResult;
import com.auditx.ingestion.service.FileUploadService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ingest")
public class FileUploadController {

    private final FileUploadService fileUploadService;

    public FileUploadController(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<BatchIngestionResult>> uploadFile(
            @RequestPart("file") FilePart filePart,
            ServerWebExchange exchange) {

        String tenantId = exchange.getAttribute("X-Tenant-Id");
        if (tenantId == null || tenantId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(new BatchIngestionResult(0, 0, 0, List.of())));
        }

        return fileUploadService.processUpload(filePart, tenantId)
                .map(result -> ResponseEntity.accepted().<BatchIngestionResult>body(result));
    }
}
