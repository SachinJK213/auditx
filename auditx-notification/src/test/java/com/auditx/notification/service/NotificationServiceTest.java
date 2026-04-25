package com.auditx.notification.service;

import com.auditx.common.dto.AlertDto;
import com.auditx.notification.model.NotificationChannelDocument;
import com.auditx.notification.repository.NotificationChannelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationChannelRepository channelRepository;

    @Mock
    private SlackNotifier slackNotifier;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(channelRepository, slackNotifier);
    }

    private AlertDto buildAlert(String alertId, String tenantId) {
        return new AlertDto(alertId, tenantId, "evt-1", "alice", 85.0,
                List.of("FAILED_LOGIN"), "OPEN", Instant.now());
    }

    private NotificationChannelDocument slackChannel(String tenantId, boolean enabled) {
        NotificationChannelDocument doc = new NotificationChannelDocument();
        doc.setId("ch-1");
        doc.setTenantId(tenantId);
        doc.setChannel("SLACK");
        doc.setWebhookUrl("https://hooks.slack.com/services/test");
        doc.setEnabled(enabled);
        doc.setCreatedAt(Instant.now());
        return doc;
    }

    @Test
    void processAlert_slackChannelEnabled_sendsSlack() {
        AlertDto alert = buildAlert("alert-1", "tenant-1");
        NotificationChannelDocument channel = slackChannel("tenant-1", true);

        when(channelRepository.findByTenantIdAndEnabled("tenant-1", true))
                .thenReturn(Flux.just(channel));
        when(slackNotifier.send(eq("https://hooks.slack.com/services/test"), anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(notificationService.processAlert(alert))
                .verifyComplete();

        verify(slackNotifier, times(1))
                .send(eq("https://hooks.slack.com/services/test"), anyString());
    }

    @Test
    void processAlert_noChannels_doesNothing() {
        AlertDto alert = buildAlert("alert-2", "tenant-2");

        when(channelRepository.findByTenantIdAndEnabled("tenant-2", true))
                .thenReturn(Flux.empty());

        StepVerifier.create(notificationService.processAlert(alert))
                .verifyComplete();

        verifyNoInteractions(slackNotifier);
    }

    @Test
    void processAlert_channelDisabled_skipped() {
        AlertDto alert = buildAlert("alert-3", "tenant-3");

        // Disabled channels are not returned by the repo query (findByTenantIdAndEnabled with true)
        when(channelRepository.findByTenantIdAndEnabled("tenant-3", true))
                .thenReturn(Flux.empty());

        StepVerifier.create(notificationService.processAlert(alert))
                .verifyComplete();

        verifyNoInteractions(slackNotifier);
    }

    @Test
    void processAlert_slackSendFails_completesWithoutError() {
        AlertDto alert = buildAlert("alert-4", "tenant-4");
        NotificationChannelDocument channel = slackChannel("tenant-4", true);

        when(channelRepository.findByTenantIdAndEnabled("tenant-4", true))
                .thenReturn(Flux.just(channel));
        when(slackNotifier.send(anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("Slack unreachable")));

        // processAlert itself should complete without propagating the error
        // because SlackNotifier.send() uses onErrorResume internally;
        // but in the test we mock the raw send to return an error to verify
        // that NotificationService handles errors gracefully
        StepVerifier.create(notificationService.processAlert(alert))
                .verifyComplete();
    }
}
