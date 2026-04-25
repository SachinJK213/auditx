# Implementation Plan: AUDITX – Identity Observability & Compliance Platform

## Overview

Build the full AUDITX platform as a Maven multi-module monorepo in dependency order: shared common library first, then infrastructure (gateway, ingestion), then processing pipeline (parser, risk-engine, LLM, alert, report), then the SDK, and finally testing and DevOps artifacts.

All services use Java 21 (with virtual threads enabled), Spring Boot 3, Spring WebFlux, MongoDB, Redis, and Kafka.

## Tasks

- [x] 1. Bootstrap Maven multi-module project structure
  - Create root `pom.xml` declaring all 9 modules: `auditx-common`, `auditx-gateway`, `auditx-ingestion`, `auditx-parser`, `auditx-risk-engine`, `auditx-llm`, `auditx-alert`, `auditx-report`, `auditx-sdk`
  - Set Java 21 as `<java.version>` and configure `maven-compiler-plugin` with source/target 21
  - Enable virtual threads in each service module by setting `spring.threads.virtual.enabled=true` in `application.yml`
  - Define shared dependency management section (Spring Boot BOM, Kafka, MongoDB, Redis, Testcontainers, JUnit 5, Mockito, JaCoCo)
  - Create each module directory with a minimal `pom.xml` inheriting from root
  - Configure JaCoCo plugin at root level with 70% line coverage threshold
  - _Requirements: 1.1, 1.2, 1.4_

- [x] 2. Implement `auditx-common` — shared library
  - [x] 2.1 Create shared DTOs and Kafka topic constants
    - Create `RawEventDto`, `StructuredEventDto`, `AlertDto`, `RiskScoreDto` Java records/classes
    - Create `KafkaTopics` constants class with `RAW_EVENTS`, `STRUCTURED_EVENTS`, `ALERTS` string constants
    - Create `PayloadType` enum with values `RAW` and `STRUCTURED`
    - Create `EventOutcome` enum with values `SUCCESS` and `FAILURE`
    - _Requirements: 1.3, 3.8_

  - [x] 2.2 Create shared exception types and utilities
    - Create `TenantNotFoundException`, `DuplicateEventException`, `KafkaPublishException` exception classes
    - Create `IdempotencyKeyGenerator` utility that generates UUID-based unique keys
    - Create `Sha256HashUtil` utility for hashing API keys
    - _Requirements: 1.3, 11.4_

  - [ ]* 2.3 Write unit tests for `auditx-common` utilities
    - Test `Sha256HashUtil` produces consistent hashes and never returns plaintext
    - Test `IdempotencyKeyGenerator` uniqueness across multiple calls
    - _Requirements: 1.3, 11.4_

- [x] 3. Implement `auditx-gateway` — API Gateway service
  - [x] 3.1 Set up Spring Cloud Gateway with routing configuration
    - Add `spring-cloud-starter-gateway` dependency
    - Configure route predicates in `application.yml` mapping path prefixes to downstream service URIs
    - Expose `GET /actuator/health` endpoint
    - _Requirements: 2.1, 2.5_

  - [x] 3.2 Implement JWT authentication filter
    - Create `JwtAuthFilter` implementing `GlobalFilter` that validates JWT signature and expiry using a configurable public key or JWKS endpoint from `application.yml`
    - Extract `tenantId`, `sub`, and `roles` claims from valid JWT and forward as `X-Tenant-Id`, `X-User-Id`, `X-Roles` headers
    - Return HTTP 401 with structured JSON error body for missing, expired, or invalid JWTs
    - _Requirements: 2.2, 2.3, 2.4, 11.2, 11.3_

  - [x] 3.3 Implement Redis-backed rate limiter
    - Add `spring-boot-starter-data-redis-reactive` dependency
    - Configure `RedisRateLimiter` bean using sliding window per `tenantId`
    - Apply rate limiter to protected routes; return HTTP 429 with `Retry-After` header when limit exceeded
    - _Requirements: 2.6, 2.7, 11.5_

  - [ ]* 3.4 Write RestAssured API tests for gateway
    - Test JWT validation: valid token passes, missing token returns 401, expired token returns 401
    - Test rate limiting: exceeding limit returns 429 with `Retry-After` header
    - Test health endpoint returns 200
    - _Requirements: 2.2, 2.3, 2.5, 2.6, 2.7, 13.3_

- [x] 4. Implement `auditx-ingestion` — Audit Ingestion service
  - [x] 4.1 Create ingestion REST endpoint and API key authentication
    - Create `RawEventController` with `POST /api/events/raw` accepting `RawEventDto` JSON body
    - Implement `ApiKeyAuthFilter` that reads `X-API-Key` header, computes SHA-256 hash, and compares against `tenants` collection; return HTTP 401 if absent or mismatched
    - Return HTTP 400 if `tenantId` cannot be resolved from the API key
    - _Requirements: 3.1, 3.2, 3.3, 9.4, 11.1_

  - [x] 4.2 Implement Redis idempotency check
    - Inject `ReactiveRedisTemplate`; before publishing, check for existing `idempotencyKey:tenantId` entry in Redis
    - If duplicate found, return HTTP 200 with original `eventId`
    - Store `idempotencyKey:tenantId → eventId` in Redis with TTL after successful publish
    - _Requirements: 3.6_

  - [x] 4.3 Implement Kafka event publisher
    - Configure `KafkaProducer` with `raw-events` topic using `KafkaTopics.RAW_EVENTS` constant
    - Publish validated `RawEventDto` to Kafka within 500ms; return HTTP 202 with `eventId` on success
    - Return HTTP 503 with structured JSON log (`tenantId`, `timestamp`, error details) if Kafka is unavailable
    - Enforce rate limit check before publishing; return HTTP 429 if quota exceeded
    - _Requirements: 3.4, 3.5, 3.7, 3.9_

  - [ ]* 4.4 Write unit and integration tests for ingestion service
    - Unit test `ApiKeyAuthFilter` with valid, missing, and invalid API keys
    - Integration test using Testcontainers (Kafka + MongoDB + Redis): POST raw event → verify Kafka message published
    - RestAssured test: valid request returns 202, duplicate idempotency key returns 200, missing API key returns 401
    - _Requirements: 13.2, 13.3, 13.6_

- [x] 5. Checkpoint — Ensure gateway and ingestion tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Implement `auditx-parser` — Parser service
  - [x] 6.1 Create Kafka consumer and parsing strategy interface
    - Configure Kafka consumer with `parser-group` consumer group listening on `raw-events` topic
    - Define `ParsingStrategy` interface with `boolean supports(String payload)` and `StructuredEventDto parse(String payload)` methods
    - Implement `RegexParsingStrategy` extracting `timestamp`, `userId`, `action`, `sourceIp`, `tenantId`, `outcome` via regex
    - Register strategies in a `ParsingStrategyRegistry` bean for extensibility without modifying existing code
    - _Requirements: 4.1, 4.2, 4.3_

  - [x] 6.2 Implement MongoDB storage and structured-events publisher
    - Store successfully parsed `StructuredEventDto` in `audit_events` collection with `tenantId` field and MongoDB index on `tenantId`
    - Store failed parse payloads in `raw_logs` collection with `parseStatus: FAILED` and `tenantId` index
    - Implement idempotency using `eventId` as deduplication key in MongoDB (upsert with `$setOnInsert`)
    - Publish successfully parsed events to `structured-events` topic using `KafkaTopics.STRUCTURED_EVENTS`
    - Emit structured JSON log per parse attempt: `eventId`, `tenantId`, `strategy`, `parseStatus`, `durationMs`
    - _Requirements: 4.4, 4.5, 4.6, 4.7, 4.8, 9.1, 9.2, 9.3_

  - [ ]* 6.3 Write unit and integration tests for parser service
    - Unit test `RegexParsingStrategy` with valid and malformed log strings
    - Unit test `ParsingStrategyRegistry` strategy selection and fallback to FAILED status
    - Integration test: consume from Kafka → verify `audit_events` document in MongoDB and message on `structured-events` topic
    - _Requirements: 13.1, 13.2_

- [x] 7. Implement `auditx-risk-engine` — Risk Engine service
  - [x] 7.1 Create Kafka consumer and risk rule loader
    - Configure Kafka consumer with `risk-engine-group` consumer group listening on `structured-events` topic
    - Implement `RiskRuleRepository` to load active `RiskRule` documents from MongoDB filtered by `tenantId`
    - Implement idempotency: skip reprocessing if `riskScore` already present on the `audit_events` document
    - _Requirements: 5.1, 5.2, 5.9_

  - [x] 7.2 Implement risk scoring engine
    - Implement `RiskScoringEngine` that sums weights of all matching `RiskRule` entries for an event (score clamped 0–100)
    - Implement "failed login threshold" rule: count failed logins for `userId` within configurable rolling window; assign max weight if threshold exceeded
    - Implement "geo-location anomaly" rule: compare event's country against `userId`'s recent login history within configurable lookback period; assign configured weight if anomaly detected
    - Assign Risk_Score of 0 and skip alert if tenant has no rules
    - _Requirements: 5.3, 5.4, 5.5, 5.8_

  - [x] 7.3 Persist risk score and publish alerts
    - Update `audit_events` document with `riskScore`, `ruleMatches`, and `computedAt` fields using `tenantId`-scoped update
    - Publish `AlertDto` to `alerts` topic when `riskScore` exceeds tenant's configured alert threshold
    - _Requirements: 5.6, 5.7, 9.2_

  - [ ]* 7.4 Write unit and integration tests for risk engine
    - Unit test `RiskScoringEngine` with various rule combinations, boundary scores (0, 100), and empty rule set
    - Unit test failed-login threshold and geo-anomaly rule evaluation
    - Integration test: consume structured event → verify risk score persisted in MongoDB and alert published to Kafka when threshold exceeded
    - _Requirements: 13.1, 13.2_

- [x] 8. Checkpoint — Ensure parser and risk engine tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Implement `auditx-llm` — LLM Intelligence service
  - [x] 9.1 Set up LLM provider abstraction and prompt templates
    - Define `LlmProvider` interface with `String complete(String prompt)` method
    - Implement `OpenAiLlmProvider` and `AzureOpenAiLlmProvider` using `WebClient`; select provider via `auditx.llm.provider` config in `application.yml`
    - Store versioned prompt templates as classpath resources: `prompts/explain.txt`, `prompts/query.txt`, `prompts/summarize.txt`
    - _Requirements: 6.1, 6.5_

  - [x] 9.2 Implement retry and fallback logic
    - Wrap `LlmProvider.complete()` with retry up to 3 attempts using exponential backoff starting at 500ms (using Reactor `retryWhen` or Spring Retry)
    - On exhausted retries, return fallback response string and log structured JSON: `tenantId`, `feature`, `attemptCount`, `errorMessage`
    - _Requirements: 6.6, 6.7_

  - [x] 9.3 Implement `/api/llm/explain`, `/api/llm/query`, `/api/llm/summarize` endpoints
    - `POST /api/llm/explain`: fetch `StructuredEventDto` from `audit_events` by `eventId` + `tenantId`, inject into explain template, call LLM, return explanation
    - `POST /api/llm/query`: translate NL question to MongoDB query via query template, enforce `tenantId` filter on generated query before execution, return results
    - `POST /api/llm/summarize`: fetch alerts by IDs scoped to `tenantId`, inject into summarize template, call LLM, return summary
    - _Requirements: 6.2, 6.3, 6.4, 6.8, 9.2_

  - [ ]* 9.4 Write unit and RestAssured tests for LLM service
    - Unit test retry logic: mock provider fails twice then succeeds; verify 3rd attempt returns result
    - Unit test fallback: mock provider always fails; verify fallback response returned and no exception thrown
    - Unit test tenant query scoping: verify `tenantId` filter always appended to generated MongoDB query
    - RestAssured test: valid explain/query/summarize requests return 200; missing tenantId returns 400
    - _Requirements: 13.1, 13.3_

- [x] 10. Implement `auditx-alert` — Alert service
  - [x] 10.1 Create Kafka consumer and alert persistence
    - Configure Kafka consumer with `alert-service-group` consumer group listening on `alerts` topic
    - Persist `AlertDto` to `alerts` MongoDB collection with fields: `alertId`, `tenantId`, `eventId`, `riskScore`, `ruleMatches`, `status`, `createdAt`; create `tenantId` index
    - Implement idempotency using `alertId` as deduplication key (skip if already exists in MongoDB)
    - _Requirements: 7.1, 7.2, 7.6, 9.1, 9.3_

  - [x] 10.2 Implement webhook and mock email dispatch with retry
    - Load per-tenant notification config from `tenants` collection (webhook URL, email address, enabled flags)
    - Dispatch webhook via `WebClient` HTTP POST with alert JSON payload; skip if webhook disabled for tenant
    - Dispatch mock email by logging structured JSON with email content; skip if email disabled for tenant
    - Retry webhook up to 3 times with exponential backoff; on failure update alert `status` to `WEBHOOK_FAILED` in MongoDB and log failure
    - _Requirements: 7.3, 7.4, 7.5, 7.7_

  - [ ]* 10.3 Write unit and integration tests for alert service
    - Unit test idempotency: duplicate `alertId` is not re-persisted or re-dispatched
    - Unit test webhook retry: mock HTTP server returns 500 three times; verify status updated to `WEBHOOK_FAILED`
    - Integration test: consume alert from Kafka → verify persisted in MongoDB and webhook called
    - _Requirements: 13.1, 13.2_

- [x] 11. Implement `auditx-report` — Report service
  - [x] 11.1 Create report generation endpoint and data assembly
    - Create `ReportController` with `POST /api/reports/generate` accepting `tenantId`, `startDate`, `endDate`, `reportType`
    - Implement `ReportDataAssembler` that queries `audit_events` (total count, breakdown by `action`, high-risk events with `riskScore >= 70`) and `alerts` collection, all scoped to `tenantId` and date range
    - Return empty-period report (not an error) when no events found for the tenant and date range
    - _Requirements: 8.1, 8.2, 8.3, 8.6, 9.2_

  - [x] 11.2 Implement Puppeteer-compatible HTML rendering interface and PDF response
    - Define `PdfRenderer` interface with `byte[] render(String htmlContent)` method
    - Implement `DefaultPdfRenderer` that converts assembled report data to HTML and returns bytes (stub/placeholder renderer acceptable for initial implementation)
    - Return PDF bytes with `Content-Type: application/pdf` and `Content-Disposition: attachment` headers
    - _Requirements: 8.4, 8.5_

  - [ ]* 11.3 Write unit and RestAssured tests for report service
    - Unit test `ReportDataAssembler` with mocked MongoDB repositories: verify correct tenant scoping and date range filtering
    - Unit test empty-period case: no events → report generated (not 4xx error)
    - RestAssured test: valid request returns 200 with `application/pdf` content type; missing `tenantId` returns 400
    - _Requirements: 13.1, 13.3_

- [x] 12. Checkpoint — Ensure LLM, alert, and report tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 13. Implement `auditx-sdk` — Spring Boot auto-configuration SDK
  - [x] 13.1 Set up auto-configuration and configuration properties
    - Create `AuditxAutoConfiguration` class annotated with `@AutoConfiguration`
    - Register it in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
    - Create `AuditxProperties` `@ConfigurationProperties(prefix = "auditx")` class with fields: `enabled`, `endpoint`, `apiKey`, `tenantId`, `async`, `retry.maxAttempts`
    - Conditionally activate all beans on `auditx.enabled=true`
    - _Requirements: 10.1, 10.2, 10.3_

  - [x] 13.2 Implement async thread pool and HTTP event sender with retry
    - Create `AuditxEventSender` that POSTs events to `auditx.endpoint` using `RestTemplate` or `WebClient`
    - When `auditx.async=true`, submit sends to a virtual-thread-per-task executor (`Executors.newVirtualThreadPerTaskExecutor()`) instead of a fixed `ThreadPoolTaskExecutor`
    - Retry failed sends up to `auditx.retry.maxAttempts` with exponential backoff; log each retry attempt
    - On exhausted retries, log structured JSON error and swallow exception (do not propagate to caller)
    - Generate unique `idempotencyKey` (UUID) per event before sending
    - _Requirements: 10.4, 10.7, 10.8, 10.9_

  - [x] 13.3 Implement `HandlerInterceptor` and `AuditxLoginEventPublisher`
    - Create `AuditxHandlerInterceptor` implementing `HandlerInterceptor`; in `afterCompletion` capture `method`, `path`, `statusCode`, `userId` (from `SecurityContextHolder`), `tenantId`, `durationMs` and send via `AuditxEventSender`
    - Create `AuditxLoginEventPublisher` bean with `publishLoginEvent(userId, outcome, sourceIp, timestamp)` method that sends a login event via `AuditxEventSender`
    - Register interceptor via `WebMvcConfigurer`
    - _Requirements: 10.5, 10.6_

  - [ ]* 13.4 Write unit tests for SDK
    - Test event serialization: `RawEventDto` serializes to expected JSON structure
    - Test successful send: mock HTTP server returns 200; verify no retry and no exception
    - Test retry on 5xx: mock server returns 500 twice then 200; verify 3 total attempts
    - Test retry exhaustion: mock server always returns 500; verify structured error logged and no exception thrown to caller
    - Test idempotency key uniqueness: generate 1000 keys; verify all unique
    - _Requirements: 10.7, 10.8, 10.9, 13.4, 13.5_

- [x] 14. Implement cross-cutting concerns — observability and multi-tenancy enforcement
  - [x] 14.1 Configure structured JSON logging across all services
    - Add `logstash-logback-encoder` dependency to each service module
    - Create `logback-spring.xml` in each service's `src/main/resources` emitting JSON with fields: `timestamp`, `level`, `service`, `tenantId`, `traceId`, `message`
    - Configure Spring Boot Actuator in each service to expose `/actuator/health` and `/actuator/metrics`
    - _Requirements: 12.1, 12.2_

  - [x] 14.2 Implement trace ID propagation
    - Add `traceId` MDC context in gateway filter and propagate via `X-Trace-Id` header on all outbound WebClient calls
    - Extract `X-Trace-Id` header in each service and populate MDC at request/message entry point
    - Include `traceId` in all Kafka consumer failure log entries (`eventId`, `topic`, `consumerGroup`, `attemptCount`, `errorMessage`)
    - _Requirements: 12.3, 12.4_

  - [x] 14.3 Enforce tenant isolation guards
    - Add `tenantId` mandatory filter validation in each service's request/message entry point; return HTTP 400 if unresolvable
    - Verify MongoDB indexes on `tenantId` exist for `audit_events`, `raw_logs`, `tenants`, `risk_rules`, `alerts` collections (create via `@Document` index annotations or `MongoMappingEvent`)
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5_

- [x] 15. Checkpoint — Ensure all service tests pass with observability wired in
  - Ensure all tests pass, ask the user if questions arise.

- [x] 16. Write integration and end-to-end tests
  - [x] 16.1 Write full ingestion-to-parse pipeline integration test
    - Use Testcontainers to spin up Kafka + MongoDB + Redis
    - POST raw event to `auditx-ingestion` → assert Kafka message on `raw-events` → assert `StructuredEventDto` document in MongoDB `audit_events` after parser processes it
    - _Requirements: 13.6_

  - [ ]* 16.2 Write cross-service Testcontainers integration tests
    - Integration test for risk engine: publish structured event to Kafka → verify risk score updated in MongoDB and alert published when threshold exceeded
    - Integration test for alert service: publish alert to Kafka → verify persisted in MongoDB and webhook dispatched
    - _Requirements: 13.2_

  - [ ]* 16.3 Write RestAssured API test suite
    - Cover all REST endpoints: ingestion (202, 200 duplicate, 401, 429, 503), LLM (explain, query, summarize), report (200 PDF, 400 missing params), gateway (401, 429, 200 health)
    - _Requirements: 13.3_

- [x] 17. DevOps — Dockerfiles and docker-compose
  - [x] 17.1 Create multi-stage Dockerfiles for each service
    - Write `Dockerfile` for each of the 7 service modules (`auditx-gateway`, `auditx-ingestion`, `auditx-parser`, `auditx-risk-engine`, `auditx-llm`, `auditx-alert`, `auditx-report`) using multi-stage build: `maven:3.9-eclipse-temurin-21` build stage → `eclipse-temurin:21-jre-alpine` runtime stage
    - Copy only the executable JAR into the runtime image
    - _Requirements: 14.1_

  - [x] 17.2 Create `docker-compose.yml` with infrastructure and services
    - Define services for MongoDB, Redis, Kafka, ZooKeeper with health check definitions
    - Define service entries for all 7 AUDITX services with `depends_on` (condition: `service_healthy`) on infrastructure
    - Use Docker internal DNS names for all inter-service URIs (no hardcoded IPs)
    - Assign distinct host ports to each service
    - Inject all configuration via environment variables referencing `.env` file
    - _Requirements: 14.2, 14.3, 14.4, 14.5, 14.6_

  - [x] 17.3 Create `.env` file with environment variable defaults
    - Define variables for: MongoDB URI, Redis host/port, Kafka bootstrap servers, JWT public key path, LLM provider + API key, per-service ports, alert thresholds
    - _Requirements: 14.3_

- [x] 18. Final checkpoint — Full platform validation
  - Ensure all tests pass and `docker-compose up` starts all services cleanly. Ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP
- Dependency order: `auditx-common` → gateway/ingestion → parser → risk-engine → llm/alert/report → sdk → cross-cutting → tests → devops
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation at each pipeline stage
- Property tests validate universal correctness properties; unit tests validate specific examples and edge cases
