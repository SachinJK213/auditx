package com.auditx.notification.controller;

import com.auditx.notification.model.NotificationChannelDocument;
import com.auditx.notification.repository.NotificationChannelRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestController
@RequestMapping("/api/notification")
public class NotificationChannelController {

    private final NotificationChannelRepository channelRepository;

    public NotificationChannelController(NotificationChannelRepository channelRepository) {
        this.channelRepository = channelRepository;
    }

    @PostMapping("/channels")
    public Mono<ResponseEntity<NotificationChannelDocument>> createChannel(
            @RequestBody NotificationChannelDocument channel) {
        channel.setCreatedAt(Instant.now());
        return channelRepository.save(channel)
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved));
    }

    @GetMapping("/channels")
    public Flux<NotificationChannelDocument> getChannels(@RequestParam String tenantId) {
        return channelRepository.findByTenantIdAndEnabled(tenantId, true);
    }

    @DeleteMapping("/channels/{id}")
    public Mono<ResponseEntity<Void>> deleteChannel(@PathVariable String id) {
        return channelRepository.deleteById(id)
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }
}
