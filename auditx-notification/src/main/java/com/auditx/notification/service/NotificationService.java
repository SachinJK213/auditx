package com.auditx.notification.service;

import com.auditx.common.dto.AlertDto;
import com.auditx.notification.repository.NotificationChannelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationChannelRepository channelRepository;
    private final SlackNotifier slackNotifier;

    public NotificationService(NotificationChannelRepository channelRepository,
                               SlackNotifier slackNotifier) {
        this.channelRepository = channelRepository;
        this.slackNotifier = slackNotifier;
    }

    public Mono<Void> processAlert(AlertDto alert) {
        String message = String.format(
                ":rotating_light: *AuditX Alert* — Tenant: `%s`\n" +
                "• User: `%s`\n• Risk Score: `%.0f`\n• Status: `%s`\n• Alert ID: `%s`",
                alert.tenantId(), alert.userId(), alert.riskScore(), alert.status(), alert.alertId()
        );

        return channelRepository.findByTenantIdAndEnabled(alert.tenantId(), true)
                .flatMap(channel -> {
                    if ("SLACK".equals(channel.getChannel()) && channel.getWebhookUrl() != null) {
                        return slackNotifier.send(channel.getWebhookUrl(), message)
                                .onErrorResume(ex -> {
                                    log.warn("Failed to send Slack notification for alert {}: {}",
                                            alert.alertId(), ex.getMessage());
                                    return Mono.empty();
                                });
                    }
                    return Mono.empty();
                })
                .then();
    }
}
