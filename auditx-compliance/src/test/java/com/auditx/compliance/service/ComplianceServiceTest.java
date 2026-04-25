package com.auditx.compliance.service;

import com.auditx.common.dto.PolicyViolationDto;
import com.auditx.compliance.model.ComplianceRecordDocument;
import com.auditx.compliance.repository.ComplianceRecordRepository;
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
class ComplianceServiceTest {

    @Mock ComplianceRecordRepository repository;
    private ComplianceService service;

    @BeforeEach
    void setUp() { service = new ComplianceService(repository); }

    private PolicyViolationDto violation(List<String> frameworks) {
        return new PolicyViolationDto("v1","t1","e1","alice","r1","DeleteRule","HIGH",
                "#action == 'DELETE'", frameworks, Instant.now());
    }

    @Test
    void recordViolation_twoFrameworks_createsTwoRecords() {
        ArgumentCaptor<ComplianceRecordDocument> captor =
                ArgumentCaptor.forClass(ComplianceRecordDocument.class);
        when(repository.save(captor.capture())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.recordViolation(violation(List.of("GDPR","SOC2"))))
                .verifyComplete();

        List<ComplianceRecordDocument> saved = captor.getAllValues();
        assertThat(saved).hasSize(2);
        assertThat(saved).extracting(ComplianceRecordDocument::getFramework)
                .containsExactlyInAnyOrder("GDPR","SOC2");
        assertThat(saved).allMatch(r -> "OPEN".equals(r.getStatus()));
        assertThat(saved).allMatch(r -> "HIGH".equals(r.getSeverity()));
        assertThat(saved).allMatch(r -> "t1".equals(r.getTenantId()));
    }

    @Test
    void recordViolation_noFrameworks_savesNothing() {
        StepVerifier.create(service.recordViolation(violation(List.of()))).verifyComplete();
        verifyNoInteractions(repository);
    }

    @Test
    void recordViolation_nullFrameworks_savesNothing() {
        StepVerifier.create(service.recordViolation(violation(null))).verifyComplete();
        verifyNoInteractions(repository);
    }

    @Test
    void recordViolation_eachRecordHasUniqueRecordId() {
        when(repository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        ArgumentCaptor<ComplianceRecordDocument> captor =
                ArgumentCaptor.forClass(ComplianceRecordDocument.class);
        when(repository.save(captor.capture())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.recordViolation(violation(List.of("GDPR","DPDP","SOC2"))))
                .verifyComplete();

        List<String> ids = captor.getAllValues().stream()
                .map(ComplianceRecordDocument::getRecordId).toList();
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void resolve_existingRecord_setsStatusResolved() {
        ComplianceRecordDocument record = new ComplianceRecordDocument();
        record.setRecordId("rec-1"); record.setTenantId("t1"); record.setStatus("OPEN");
        when(repository.findByRecordId("rec-1")).thenReturn(Mono.just(record));
        when(repository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.resolve("t1","rec-1"))
                .assertNext(r -> {
                    assertThat(r.getStatus()).isEqualTo("RESOLVED");
                    assertThat(r.getResolvedAt()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void resolve_wrongTenant_returnsEmpty() {
        ComplianceRecordDocument record = new ComplianceRecordDocument();
        record.setRecordId("rec-1"); record.setTenantId("tenant-OTHER");
        when(repository.findByRecordId("rec-1")).thenReturn(Mono.just(record));

        StepVerifier.create(service.resolve("tenant-MINE","rec-1")).verifyComplete();
        verify(repository, never()).save(any());
    }
}
