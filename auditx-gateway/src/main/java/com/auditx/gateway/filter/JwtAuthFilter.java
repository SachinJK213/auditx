package com.auditx.gateway.filter;

import com.auditx.common.util.MdcUtil;
import com.auditx.gateway.security.JwtValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Global JWT authentication filter for Spring Cloud Gateway.
 *
 * <p>Validates the Bearer token on every request except actuator paths.
 * On success, forwards X-Tenant-Id, X-User-Id, X-Roles, and X-Trace-Id headers
 * to downstream services and populates MDC for structured logging.
 * On failure, returns HTTP 401 with a JSON error body.
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";
    private static final List<String> SKIP_PATHS = List.of("/actuator");

    private final JwtValidator jwtValidator;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtValidator jwtValidator, ObjectMapper objectMapper) {
        this.jwtValidator = jwtValidator;
        this.objectMapper = objectMapper;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Skip authentication for actuator endpoints
        if (SKIP_PATHS.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, "Missing or malformed Authorization header");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            Map<String, Object> claims = jwtValidator.validateAndExtractClaims(token);

            String tenantId = claimAsString(claims, "tenantId");
            String userId   = claimAsString(claims, "sub");
            String roles    = claimAsString(claims, "roles");

            // Resolve or generate trace ID
            String traceId = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
            if (traceId == null || traceId.isBlank()) {
                traceId = UUID.randomUUID().toString();
            }

            // Populate MDC (best-effort in reactive context — cleared after response)
            MdcUtil.setRequestContext(traceId, tenantId, userId);

            final String finalTraceId = traceId;

            // Mutate request to add downstream headers
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-Tenant-Id", tenantId != null ? tenantId : "")
                    .header("X-User-Id",   userId   != null ? userId   : "")
                    .header("X-Roles",     roles    != null ? roles    : "")
                    .header("X-Trace-Id",  finalTraceId)
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build())
                    .doFinally(signal -> MdcUtil.clear());

        } catch (JwtException ex) {
            log.warn("JWT validation failed: {}", ex.getMessage());
            return unauthorized(exchange, ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected error during JWT validation", ex);
            return unauthorized(exchange, "Token validation error");
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of(
                "error",   "Unauthorized",
                "message", message != null ? message : "Authentication required"
        );

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = "{\"error\":\"Unauthorized\"}".getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private String claimAsString(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        if (value == null) return null;
        if (value instanceof List<?> list) {
            // roles may be a list — join with comma
            return String.join(",", list.stream().map(Object::toString).toList());
        }
        return value.toString();
    }
}
