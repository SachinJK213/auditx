package com.auditx.alert.service;

import com.auditx.alert.model.AlertDocument;
import com.auditx.alert.model.TenantDocument;
import com.auditx.alert.repository.AlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock WebClient webClient;
    @Mock WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock WebClient.RequestBodySpec requestBodySpec;
    @Mock WebClient.ResponseSpec responseSpec;
    @Mock AlertRepository alertRepository;

    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new NotificationDispatcher(webClient, alertRepository);
    }

    private AlertDocument alert() {
        AlertDocument a = new AlertDocument();
        a.setAlertId("a1"); a.setTenantId("t1"); a.setRiskScore(80.0);
        return a;
    }

    private TenantDocument tenant(boolean webhook, boolean email, String webhookUrl) {
        TenantDocument t = new TenantDocument();
        t.setWebhookEnabled(webhook); t.setEmailEnabled(email);
        t.setWebhookUrl(webhookUrl); t.setAlertEmail("sec@acme.com");
        return t;
    }

    @Test
    void dispatch_bothDisabled_completesWithNoWebClientCall() {
        StepVerifier.create(dispatcher.dispatch(alert(), tenant(false, false, null)))
                .verifyComplete();
        verifyNoInteractions(webClient);
    }

    @Test
    void dispatch_emailEnabledOnly_completesWithNoWebClientCall() {
        StepVerifier.create(dispatcher.dispatch(alert(), tenant(false, true, null)))
                .verifyComplete();
        verifyNoInteractions(webClient);
    }

    @Test
    void dispatch_webhookEnabled_callsWebhookUrl() {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri("https://hook.acme.com/alert");
        when(requestBodySpec.bodyValue(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

        StepVerifier.create(dispatcher.dispatch(alert(), tenant(true, false, "https://hook.acme.com/alert")))
                .verifyComplete();

        verify(webClient).post();
    }

    @Test
    void dispatch_webhookFails_setsStatusWebhookFailed() {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri(anyString());
        when(requestBodySpec.bodyValue(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Void.class))
                .thenReturn(Mono.error(new RuntimeException("Connection refused")));
        when(alertRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        AlertDocument alert = alert();
        StepVerifier.create(dispatcher.dispatch(alert, tenant(true, false, "https://bad-url")))
                .verifyComplete();

        verify(alertRepository).save(argThat(a -> "WEBHOOK_FAILED".equals(a.getStatus())));
    }
}
