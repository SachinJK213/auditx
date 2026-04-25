package com.auditx.ingestion.filter;

import com.auditx.common.util.Sha256HashUtil;
import com.auditx.ingestion.repository.TenantRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiKeyAuthFilter implements WebFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String TENANT_ID_ATTR = "X-Tenant-Id";
    private static final Set<String> PROTECTED_PREFIXES = Set.of(
            "/api/events/raw",
            "/api/v1/ingest/upload",
            "/webhook/sources"
    );

    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthFilter(TenantRepository tenantRepository, ObjectMapper objectMapper) {
        this.tenantRepository = tenantRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        boolean isProtected = PROTECTED_PREFIXES.stream().anyMatch(path::startsWith);
        if (!isProtected) {
            return chain.filter(exchange);
        }

        String apiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            return writeUnauthorized(exchange, "Invalid API key");
        }

        String apiKeyHash = Sha256HashUtil.hash(apiKey);
        return tenantRepository.findByApiKeyHash(apiKeyHash)
                .flatMap(tenant -> {
                    exchange.getAttributes().put(TENANT_ID_ATTR, tenant.getTenantId());
                    return chain.filter(exchange);
                })
                .switchIfEmpty(Mono.defer(() -> writeUnauthorized(exchange, "Invalid API key")));
    }

    private Mono<Void> writeUnauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(Map.of("error", "Unauthorized", "message", message));
        } catch (JsonProcessingException e) {
            body = "{\"error\":\"Unauthorized\",\"message\":\"Invalid API key\"}".getBytes();
        }
        var buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
