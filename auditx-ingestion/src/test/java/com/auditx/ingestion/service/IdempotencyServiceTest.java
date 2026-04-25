package com.auditx.ingestion.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock ReactiveRedisTemplate<String, String> redisTemplate;
    @Mock ReactiveValueOperations<String, String> valueOps;

    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new IdempotencyService(redisTemplate);
    }

    @Test
    void checkDuplicate_keyExists_returnsOptionalWithEventId() {
        when(valueOps.get("idempotency:t1:key-1")).thenReturn(Mono.just("evt-abc"));

        StepVerifier.create(service.checkDuplicate("key-1", "t1"))
                .assertNext(opt -> {
                    assertThat(opt).isPresent();
                    assertThat(opt.get()).isEqualTo("evt-abc");
                })
                .verifyComplete();
    }

    @Test
    void checkDuplicate_keyAbsent_returnsOptionalEmpty() {
        when(valueOps.get(anyString())).thenReturn(Mono.empty());

        StepVerifier.create(service.checkDuplicate("key-new", "t1"))
                .assertNext(opt -> assertThat(opt).isEmpty())
                .verifyComplete();
    }

    @Test
    void store_writesWithCorrectKeyAndTtl() {
        when(valueOps.set(eq("idempotency:t1:key-1"), eq("evt-1"), eq(Duration.ofHours(24))))
                .thenReturn(Mono.just(Boolean.TRUE));

        StepVerifier.create(service.store("key-1", "t1", "evt-1"))
                .verifyComplete();

        verify(valueOps).set("idempotency:t1:key-1", "evt-1", Duration.ofHours(24));
    }

    @Test
    void checkDuplicate_keyFormatIsTenantScoped() {
        when(valueOps.get("idempotency:tenant-A:k")).thenReturn(Mono.just("e1"));
        when(valueOps.get("idempotency:tenant-B:k")).thenReturn(Mono.empty());

        // same key, different tenants → different Redis keys
        StepVerifier.create(service.checkDuplicate("k", "tenant-A"))
                .assertNext(opt -> assertThat(opt).isPresent()).verifyComplete();

        StepVerifier.create(service.checkDuplicate("k", "tenant-B"))
                .assertNext(opt -> assertThat(opt).isEmpty()).verifyComplete();
    }
}
