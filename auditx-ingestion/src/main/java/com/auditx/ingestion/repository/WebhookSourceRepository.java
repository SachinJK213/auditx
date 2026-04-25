package com.auditx.ingestion.repository;

import com.auditx.ingestion.model.WebhookSourceDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface WebhookSourceRepository extends ReactiveMongoRepository<WebhookSourceDocument, String> {

    Mono<WebhookSourceDocument> findBySourceId(String sourceId);
}
