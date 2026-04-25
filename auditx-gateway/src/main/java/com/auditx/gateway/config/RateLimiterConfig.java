package com.auditx.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Redis-backed rate limiter configuration.
 *
 * <p>The {@code tenantKeyResolver} bean extracts the tenant identifier from the
 * {@code X-Tenant-Id} header (set by {@link com.auditx.gateway.filter.JwtAuthFilter})
 * and falls back to {@code "anonymous"} when the header is absent.
 *
 * <p>The {@link RedisRateLimiter} is wired into the gateway's default-filters via
 * {@code application.yml} using the token-bucket algorithm (replenish rate / burst capacity).
 */
@Configuration
public class RateLimiterConfig {

    /**
     * Key resolver that partitions rate-limit buckets by tenant.
     * Referenced in application.yml as {@code "#{@tenantKeyResolver}"}.
     */
    @Bean
    public KeyResolver tenantKeyResolver() {
        return exchange -> Mono.justOrEmpty(
                exchange.getRequest().getHeaders().getFirst("X-Tenant-Id")
        ).defaultIfEmpty("anonymous");
    }

    /**
     * Default Redis rate limiter: 10 req/s replenish, burst of 20, 1 token per request.
     * These defaults are overridden by the {@code application.yml} filter args at runtime.
     */
    @Bean
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(10, 20, 1);
    }
}
