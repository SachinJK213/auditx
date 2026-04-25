package com.auditx.alert.service;

import com.auditx.alert.model.AlertDocument;
import com.auditx.alert.model.TenantDocument;
import com.auditx.alert.repository.AlertRepository;
import com.auditx.alert.repository.TenantRepository;
import com.auditx.common.dto.AlertDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock AlertRepository alertRepository;
    @Mock TenantRepository tenantRepository;
    @Mock NotificationDispatcher notificationDispatcher;

    private AlertService service;

    @BeforeEach
    void setUp() {
        service = new AlertService(alertRepository, tenantRepository, notificationDispatcher);
    }

    private AlertDto alert(String alertId) {
        return new AlertDto(alertId, "t1", "evt-1", "alice", 85.0,
                List.of("FailedLogin"), "OPEN", Instant.now());
    }

    @Test
    void process_newAlert_persistsAndDispatches() {
        when(alertRepository.findByAlertId("alert-1")).thenReturn(Mono.empty());
        ArgumentCaptor<AlertDocument> captor = ArgumentCaptor.forClass(AlertDocument.class);
        when(alertRepository.save(captor.capture())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        TenantDocument tenant = new TenantDocument();
        tenant.setTenantId("t1"); tenant.setWebhookEnabled(false); tenant.setEmailEnabled(false);
        when(tenantRepository.findByTenantId("t1")).thenReturn(Mono.just(tenant));
        when(notificationDispatcher.dispatch(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(service.process(alert("alert-1"))).verifyComplete();

        AlertDocument saved = captor.getValue();
        assertThat(saved.getAlertId()).isEqualTo("alert-1");
        assertThat(saved.getStatus()).isEqualTo("OPEN");
        assertThat(saved.getEscalationLevel()).isEqualTo(1);
        assertThat(saved.getRiskScore()).isEqualTo(85.0);
        verify(notificationDispatcher).dispatch(any(), eq(tenant));
    }

    @Test
    void process_duplicateAlertId_skipsWithoutPersisting() {
        AlertDocument existing = new AlertDocument();
        existing.setAlertId("alert-dup");
        when(alertRepository.findByAlertId("alert-dup")).thenReturn(Mono.just(existing));

        StepVerifier.create(service.process(alert("alert-dup"))).verifyComplete();

        verify(alertRepository, never()).save(any());
        verifyNoInteractions(notificationDispatcher);
    }

    @Test
    void process_tenantNotFound_completesWithoutDispatch() {
        when(alertRepository.findByAlertId(any())).thenReturn(Mono.empty());
        when(alertRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(tenantRepository.findByTenantId("t1")).thenReturn(Mono.empty());

        StepVerifier.create(service.process(alert("alert-2"))).verifyComplete();
        verify(notificationDispatcher, never()).dispatch(any(), any());
    }
}
