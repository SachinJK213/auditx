package com.auditx.livestream.web;

import com.auditx.livestream.dto.LiveEventDto;
import com.auditx.livestream.service.EventBroadcastService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;

@WebFluxTest(LiveStreamController.class)
class LiveStreamControllerTest {

    @Autowired WebTestClient webTestClient;
    @MockBean  EventBroadcastService broadcastService;

    private LiveEventDto sampleEvent(String tenantId) {
        return new LiveEventDto("e1", tenantId, "alice", "LOGIN", "10.0.0.1", "SUCCESS",
                Instant.now(), 40.0, "MEDIUM", List.of(), "PROCESSED", Instant.now());
    }

    @Test
    void streamLive_returnsSseContentType() {
        when(broadcastService.subscribe("t1")).thenReturn(Flux.empty());
        when(broadcastService.subscriberCount("t1")).thenReturn(0);

        webTestClient.get()
                .uri("/api/v1/stream/live?tenantId=t1")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);
    }

    @Test
    void streamLive_emitsEventAsJson() {
        when(broadcastService.subscribe("t1"))
                .thenReturn(Flux.just(sampleEvent("t1")).delayElements(Duration.ZERO));
        when(broadcastService.subscriberCount("t1")).thenReturn(0);

        webTestClient.get()
                .uri("/api/v1/stream/live?tenantId=t1")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(LiveEventDto.class)
                .hasSize(1)
                .contains(sampleEvent("t1"));
    }

    @Test
    void statusEndpoint_returnsTenantIdAndSubscriberCount() {
        when(broadcastService.subscriberCount("t1")).thenReturn(3);

        webTestClient.get()
                .uri("/api/v1/stream/status?tenantId=t1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.tenantId").isEqualTo("t1")
                .jsonPath("$.activeSubscribers").isEqualTo(3);
    }
}
