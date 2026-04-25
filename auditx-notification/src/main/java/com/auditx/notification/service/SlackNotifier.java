package com.auditx.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class SlackNotifier {

    private static final Logger log = LoggerFactory.getLogger(SlackNotifier.class);

    private final WebClient webClient;

    public SlackNotifier(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public Mono<Void> send(String webhookUrl, String message) {
        Map<String, String> body = Map.of("text", message);
        return webClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .then()
                .onErrorResume(ex -> {
                    log.warn("Slack notification failed: {}", ex.getMessage());
                    return Mono.empty();
                });
    }
}
