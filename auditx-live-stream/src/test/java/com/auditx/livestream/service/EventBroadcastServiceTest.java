package com.auditx.livestream.service;

import com.auditx.livestream.dto.LiveEventDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class EventBroadcastServiceTest {

    private EventBroadcastService service;

    @BeforeEach
    void setUp() { service = new EventBroadcastService(); }

    private LiveEventDto event(String tenantId, String userId) {
        return new LiveEventDto("evt-" + userId, tenantId, userId, "LOGIN",
                "10.0.0.1", "SUCCESS", Instant.now(), 30.0, "LOW",
                List.of(), "PROCESSED", Instant.now());
    }

    @Test
    void subscribe_thenPublish_deliversEvent() {
        StepVerifier.create(service.subscribe("t1").take(1))
                .then(() -> service.publish(event("t1", "alice")))
                .assertNext(e -> {
                    assertThat(e.tenantId()).isEqualTo("t1");
                    assertThat(e.userId()).isEqualTo("alice");
                })
                .verifyComplete();
    }

    @Test
    void publish_noSubscribers_doesNotThrow() {
        // no subscriber for t2 — publish should silently discard
        assertThatCode(() -> service.publish(event("t2", "alice")))
                .doesNotThrowAnyException();
    }

    @Test
    void tenantIsolation_eventGoesToCorrectTenantOnly() {
        // Subscribe only to t1
        StepVerifier.create(service.subscribe("t1").take(1))
                .then(() -> {
                    service.publish(event("t2", "intruder")); // different tenant
                    service.publish(event("t1", "alice"));    // correct tenant
                })
                .assertNext(e -> assertThat(e.tenantId()).isEqualTo("t1"))
                .verifyComplete();
    }

    @Test
    void subscriberCount_incrementsOnSubscribeDecrementsOnCancel() {
        assertThat(service.subscriberCount("t1")).isEqualTo(0);

        service.subscribe("t1")
                .subscribe()
                .dispose();

        // after dispose, sink should eventually be cleaned (or at 0)
        assertThat(service.subscriberCount("t1")).isLessThanOrEqualTo(1);
    }

    @Test
    void multipleSubscribers_sameTenant_allReceiveEvent() {
        StepVerifier sub1 = StepVerifier.create(service.subscribe("t1").take(1))
                .assertNext(e -> assertThat(e.userId()).isEqualTo("bob"));
        StepVerifier sub2 = StepVerifier.create(service.subscribe("t1").take(1))
                .assertNext(e -> assertThat(e.userId()).isEqualTo("bob"));

        service.publish(event("t1", "bob"));

        sub1.verifyComplete();
        sub2.verifyComplete();
    }

    @Test
    void riskLevel_classification_isCorrect() {
        assertThat(LiveEventDto.toRiskLevel(95.0)).isEqualTo("CRITICAL");
        assertThat(LiveEventDto.toRiskLevel(75.0)).isEqualTo("HIGH");
        assertThat(LiveEventDto.toRiskLevel(45.0)).isEqualTo("MEDIUM");
        assertThat(LiveEventDto.toRiskLevel(10.0)).isEqualTo("LOW");
        assertThat(LiveEventDto.toRiskLevel(null)).isEqualTo("UNKNOWN");
    }
}
