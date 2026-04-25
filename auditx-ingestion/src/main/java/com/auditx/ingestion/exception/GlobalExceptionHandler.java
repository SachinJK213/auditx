package com.auditx.ingestion.exception;

import com.auditx.common.exception.KafkaPublishException;
import com.auditx.common.exception.TenantNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(KafkaPublishException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleKafkaPublishException(
            KafkaPublishException ex, ServerWebExchange exchange) {

        String tenantId = exchange.getAttribute("X-Tenant-Id");
        log.error("{\"tenantId\":\"{}\",\"timestamp\":\"{}\",\"error\":\"{}\"}",
                tenantId, Instant.now(), ex.getMessage());

        Map<String, Object> body = Map.of(
                "error", "Service Unavailable",
                "message", ex.getMessage(),
                "tenantId", tenantId != null ? tenantId : "unknown",
                "timestamp", Instant.now().toString()
        );
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body));
    }

    @ExceptionHandler(TenantNotFoundException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleTenantNotFoundException(
            TenantNotFoundException ex) {
        Map<String, Object> body = Map.of(
                "error", "Bad Request",
                "message", ex.getMessage()
        );
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        Map<String, Object> body = Map.of(
                "error", "Internal Server Error",
                "message", "An unexpected error occurred"
        );
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body));
    }
}
