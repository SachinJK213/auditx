package com.auditx.livestream.service;

import com.auditx.livestream.dto.LiveEventDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class EventBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(EventBroadcastService.class);

    // One multicast sink per tenant. Buffer 5000 events so slow clients don't drop events
    // if they briefly pause (tab hidden, etc). DROP_OLDEST when buffer is full.
    private static final int BUFFER_SIZE = 5000;

    private final ConcurrentHashMap<String, Sinks.Many<LiveEventDto>> sinks = new ConcurrentHashMap<>();

    public void publish(LiveEventDto event) {
        Sinks.Many<LiveEventDto> sink = sinks.get(event.tenantId());
        if (sink == null) {
            return; // no active subscribers for this tenant, skip
        }
        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result.isFailure() && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
            log.warn("Broadcast drop for tenant={} reason={}", event.tenantId(), result);
        }
    }

    public Flux<LiveEventDto> subscribe(String tenantId) {
        Sinks.Many<LiveEventDto> sink = sinks.computeIfAbsent(tenantId, id ->
                Sinks.many().multicast().onBackpressureBuffer(BUFFER_SIZE, false));
        return sink.asFlux()
                .doFinally(signal -> removeIfEmpty(tenantId));
    }

    private void removeIfEmpty(String tenantId) {
        Sinks.Many<LiveEventDto> sink = sinks.get(tenantId);
        if (sink != null && sink.currentSubscriberCount() == 0) {
            sinks.remove(tenantId, sink);
        }
    }

    public int subscriberCount(String tenantId) {
        Sinks.Many<LiveEventDto> sink = sinks.get(tenantId);
        return sink == null ? 0 : sink.currentSubscriberCount();
    }
}
