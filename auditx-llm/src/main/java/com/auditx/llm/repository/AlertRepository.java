package com.auditx.llm.repository;

import com.auditx.llm.model.AlertDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

import java.util.List;

public interface AlertRepository extends ReactiveMongoRepository<AlertDocument, String> {
    Flux<AlertDocument> findByAlertIdInAndTenantId(List<String> alertIds, String tenantId);
}
