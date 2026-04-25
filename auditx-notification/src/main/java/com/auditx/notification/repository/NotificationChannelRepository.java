package com.auditx.notification.repository;

import com.auditx.notification.model.NotificationChannelDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface NotificationChannelRepository extends ReactiveMongoRepository<NotificationChannelDocument, String> {

    Flux<NotificationChannelDocument> findByTenantIdAndEnabled(String tenantId, boolean enabled);
}
