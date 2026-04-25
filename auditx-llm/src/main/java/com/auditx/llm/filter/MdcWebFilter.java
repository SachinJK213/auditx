package com.auditx.llm.filter;

import com.auditx.common.util.MdcUtil;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;

/**
 * WebFlux filter that populates MDC with traceId, tenantId, and userId
 * at the HTTP request boundary and clears MDC after the response.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class MdcWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        String tenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-Id");
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");

        MdcUtil.setRequestContext(traceId, tenantId, userId);

        final String finalTraceId = traceId;
        return chain.filter(exchange)
                .contextWrite(Context.of("traceId", finalTraceId))
                .doFinally(signal -> MdcUtil.clear());
    }
}
