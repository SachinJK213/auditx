package com.auditx.piidetector.repository;

import com.auditx.piidetector.model.PiiFindingDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

import java.time.Instant;

public interface PiiFindingRepository extends ReactiveMongoRepository<PiiFindingDocument, String> {

    Flux<PiiFindingDocument> findByTenantIdAndScannedAtAfter(String tenantId, Instant after);
}
