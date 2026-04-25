package com.auditx.ingestion.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

@Service
public class IdempotencyService {

    private static final Duration TTL = Duration.ofHours(24);
    private static final String KEY_PREFIX = "idempotency:";

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public IdempotencyService(@Qualifier("reactiveStringRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Returns Optional.of(eventId) if a duplicate is found, Optional.empty() otherwise.
     */
    public Mono<Optional<String>> checkDuplicate(String idempotencyKey, String tenantId) {
        String key = buildKey(tenantId, idempotencyKey);
        return redisTemplate.opsForValue().get(key)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }

    /**
     * Stores the idempotency key → eventId mapping with a 24-hour TTL.
     */
    public Mono<Void> store(String idempotencyKey, String tenantId, String eventId) {
        String key = buildKey(tenantId, idempotencyKey);
        return redisTemplate.opsForValue().set(key, eventId, TTL).then();
    }

    private String buildKey(String tenantId, String idempotencyKey) {
        return KEY_PREFIX + tenantId + ":" + idempotencyKey;
    }
}
