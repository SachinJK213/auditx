package com.auditx.alert.service;

import com.auditx.alert.model.AlertDocument;
import com.auditx.alert.model.TenantDocument;
import com.auditx.alert.repository.AlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final WebClient webClient;
    private final AlertRepository alertRepository;

    public NotificationDispatcher(WebClient webClient, AlertRepository alertRepository) {
        this.webClient = webClient;
        this.alertRepository = alertRepository;
    }

    public Mono<Void> dispatch(AlertDocument alert, TenantDocument tenant) {
        Mono<Void> webhookMono = tenant.isWebhookEnabled()
                ? dispatchWebhook(alert, tenant.getWebhookUrl())
                : Mono.empty();

        Mono<Void> emailMono = tenant.isEmailEnabled()
                ? dispatchEmail(alert, tenant.getAlertEmail())
                : Mono.empty();

        return Mono.when(webhookMono, emailMono);
    }

    private Mono<Void> dispatchWebhook(AlertDocument alert, String webhookUrl) {
        return webClient.post()
                .uri(webhookUrl)
                .bodyValue(alert)
                .retrieve()
                .bodyToMono(Void.class)
                .retryWhen(Retry.backoff(3, Duration.ofMillis(500)))
                .onErrorResume(ex -> {
                    log.error("{\"event\":\"webhook_failed\",\"alertId\":\"{}\",\"tenantId\":\"{}\",\"webhookUrl\":\"{}\",\"error\":\"{}\"}",
                            alert.getAlertId(), alert.getTenantId(), webhookUrl, ex.getMessage());
                    alert.setStatus("WEBHOOK_FAILED");
                    return alertRepository.save(alert).then();
                });
    }

    private Mono<Void> dispatchEmail(AlertDocument alert, String emailAddress) {
        log.info("{\"event\":\"email_dispatch\",\"to\":\"{}\",\"subject\":\"AUDITX Alert\",\"alertId\":\"{}\",\"tenantId\":\"{}\",\"riskScore\":{}}",
                emailAddress, alert.getAlertId(), alert.getTenantId(), alert.getRiskScore());
        return Mono.empty();
    }
}
