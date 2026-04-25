package com.auditx.livestream.web;

import com.auditx.livestream.dto.LiveEventDto;
import com.auditx.livestream.service.EventBroadcastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/stream")
public class LiveStreamController {

    private static final Logger log = LoggerFactory.getLogger(LiveStreamController.class);

    private final EventBroadcastService broadcastService;

    public LiveStreamController(EventBroadcastService broadcastService) {
        this.broadcastService = broadcastService;
    }

    /**
     * SSE stream of all events for a tenant as they arrive.
     * Connect with: EventSource('/api/v1/stream/live?tenantId=tenant-demo')
     *
     * A heartbeat is sent every 15 seconds to keep the connection alive through proxies.
     */
    @GetMapping(value = "/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<?>> streamLive(@RequestParam String tenantId) {
        log.info("SSE client connected tenantId={} subscribers={}", tenantId,
                broadcastService.subscriberCount(tenantId) + 1);

        Flux<ServerSentEvent<?>> events = broadcastService.subscribe(tenantId)
                .map(event -> ServerSentEvent.builder()
                        .id(event.eventId())
                        .event("log")
                        .data(event)
                        .build());

        // Heartbeat keeps the connection alive through nginx / load balancers
        Flux<ServerSentEvent<?>> heartbeat = Flux.interval(Duration.ofSeconds(15))
                .map(tick -> ServerSentEvent.builder()
                        .event("heartbeat")
                        .data(Map.of("ts", System.currentTimeMillis()))
                        .build());

        return Flux.merge(events, heartbeat)
                .doOnCancel(() -> log.info("SSE client disconnected tenantId={}", tenantId))
                .doOnError(e -> log.warn("SSE error tenantId={} {}", tenantId, e.getMessage()));
    }

    @GetMapping("/status")
    public Map<String, Object> status(@RequestParam String tenantId) {
        return Map.of(
                "tenantId", tenantId,
                "activeSubscribers", broadcastService.subscriberCount(tenantId)
        );
    }
}
