package com.auditx.parser.repository;

import com.auditx.parser.model.RawLogDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface RawLogRepository extends ReactiveMongoRepository<RawLogDocument, String> {
}
