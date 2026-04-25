package com.auditx.riskengine.service;

import com.auditx.common.dto.StructuredEventDto;
import com.auditx.riskengine.model.UserRiskProfileDocument;
import com.auditx.riskengine.repository.UserRiskProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRiskAggregationServiceTest {

    @Mock UserRiskProfileRepository repo;
    private UserRiskAggregationService service;

    @BeforeEach
    void setUp() { service = new UserRiskAggregationService(repo); }

    private StructuredEventDto event(String userId) {
        return new StructuredEventDto("e1","t1", userId,"LOGIN","1.1.1.1","SUCCESS",
                Instant.now(),null,null,null);
    }

    @Test
    void aggregate_newUser_createsProfileWithScore() {
        when(repo.findByTenantIdAndUserId("t1","alice")).thenReturn(Mono.empty());
        ArgumentCaptor<UserRiskProfileDocument> captor = ArgumentCaptor.forClass(UserRiskProfileDocument.class);
        when(repo.save(captor.capture())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.aggregate(event("alice"), 60.0)).verifyComplete();

        UserRiskProfileDocument saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo("t1");
        assertThat(saved.getUserId()).isEqualTo("alice");
        assertThat(saved.getCumulativeScore()).isEqualTo(60.0);
        assertThat(saved.getEventCount()).isEqualTo(1);
        assertThat(saved.getHighRiskEventCount()).isEqualTo(0); // 60 < 70
    }

    @Test
    void aggregate_existingUser_updatesWithWeightedAverage() {
        UserRiskProfileDocument existing = new UserRiskProfileDocument();
        existing.setTenantId("t1"); existing.setUserId("alice");
        existing.setCumulativeScore(40.0); existing.setEventCount(4);
        existing.setHighRiskEventCount(0);

        when(repo.findByTenantIdAndUserId("t1","alice")).thenReturn(Mono.just(existing));
        ArgumentCaptor<UserRiskProfileDocument> captor = ArgumentCaptor.forClass(UserRiskProfileDocument.class);
        when(repo.save(captor.capture())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // WMA: (40*4 + 80) / 5 = 48
        StepVerifier.create(service.aggregate(event("alice"), 80.0)).verifyComplete();

        UserRiskProfileDocument saved = captor.getValue();
        assertThat(saved.getCumulativeScore()).isEqualTo(48.0);
        assertThat(saved.getEventCount()).isEqualTo(5);
        assertThat(saved.getHighRiskEventCount()).isEqualTo(1); // 80 > 70
    }

    @Test
    void aggregate_scoreAbove100_clampedAt100() {
        when(repo.findByTenantIdAndUserId("t1","alice")).thenReturn(Mono.empty());
        when(repo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.aggregate(event("alice"), 150.0)).verifyComplete();

        verify(repo).save(argThat(doc -> doc.getCumulativeScore() == 100.0));
    }

    @Test
    void aggregate_nullUserId_skipsWithoutError() {
        StructuredEventDto noUser = new StructuredEventDto("e1","t1",null,"LOGIN","1.1.1.1",
                "SUCCESS",Instant.now(),null,null,null);
        StepVerifier.create(service.aggregate(noUser, 50.0)).verifyComplete();
        verifyNoInteractions(repo);
    }

    @Test
    void aggregate_blankUserId_skipsWithoutError() {
        StructuredEventDto blankUser = new StructuredEventDto("e1","t1","  ","LOGIN","1.1.1.1",
                "SUCCESS",Instant.now(),null,null,null);
        StepVerifier.create(service.aggregate(blankUser, 50.0)).verifyComplete();
        verifyNoInteractions(repo);
    }
}
