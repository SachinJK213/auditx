# Requirements Document

## Introduction

AUDITX is a production-grade micro SaaS platform for Identity Observability and Compliance. It ingests raw audit logs and structured events from client applications, parses and enriches them, applies risk scoring, generates alerts, and produces compliance reports — all in a multi-tenant, reactive, event-driven architecture built on Java 21 (with virtual threads), Spring Boot, Spring WebFlux, MongoDB, Redis, Kafka, and Docker.

The platform is composed of seven backend services, a shared SDK module, and an API Gateway. All data is tenant-scoped. The system is designed for high-throughput ingestion, loose coupling via Kafka, and idempotent processing.

The platform supports both **cloud** and **on-premises** deployment models. Relational database storage (MySQL, PostgreSQL, or Oracle) is supported alongside MongoDB, selectable via Spring profiles. All REST APIs are documented via Swagger/OpenAPI 3.0.

---

## Glossary

- **AUDITX**: The platform described in this document.
- **API_Gateway**: The Spring Cloud Gateway service that routes requests and validates JWTs.
- **Ingestion_Service**: The service that accepts raw and structured audit events via REST and publishes them to Kafka.
- **Parser_Service**: The Kafka consumer service that converts raw logs into structured JSON and stores them in MongoDB.
- **Risk_Engine**: The Kafka consumer service that applies rule-based risk scoring to structured events.
- **LLM_Service**: The service that integrates with an LLM provider (OpenAI or Azure OpenAI) for incident explanation, NL-to-query, and alert summarization.
- **Alert_Service**: The Kafka consumer service that dispatches notifications via webhook or email when risk thresholds are exceeded.
- **Report_Service**: The service that generates PDF compliance reports.
- **SDK**: The `auditx-sdk` Java library that client Spring Boot applications embed to auto-capture and forward events.
- **Tenant**: An isolated organizational unit identified by a `tenantId`. All data is scoped to a tenant.
- **API_Key**: A secret credential issued per tenant, used to authenticate calls to the Ingestion_Service.
- **JWT**: A signed JSON Web Token used to authenticate dashboard and management API calls through the API_Gateway.
- **Risk_Score**: A numeric value (0–100) computed by the Risk_Engine representing the threat level of an event or session.
- **Risk_Rule**: A configurable rule stored in MongoDB that defines conditions and weights for risk scoring.
- **Alert**: A notification record created when a Risk_Score exceeds a configured threshold.
- **Raw_Log**: An unstructured or semi-structured string payload submitted to the Ingestion_Service.
- **Structured_Event**: A normalized JSON document stored in the `audit_events` MongoDB collection.
- **Kafka_Topic**: A named Kafka channel used for async inter-service communication.
- **Rate_Limiter**: A Redis-backed component that enforces per-tenant request rate limits.
- **Idempotency_Key**: A client-supplied or system-generated key used to deduplicate event processing.
- **MDC**: Mapped Diagnostic Context — a thread-local (or reactive-context-local) map of key-value pairs that Logback automatically includes in every log entry emitted on that thread.
- **MdcFilter**: A servlet filter or WebFlux `WebFilter` that populates MDC at the HTTP request boundary and clears it after the response.
- **MdcKafkaConsumerInterceptor**: A Kafka `ConsumerInterceptor` that restores MDC context from Kafka message headers before a listener method executes.
- **MdcUtil**: A shared utility class in `auditx-common` providing consistent MDC key constants and helper methods used by all services.
- **Deployment_Profile**: A Spring profile (`cloud`, `onprem`) that activates environment-specific infrastructure configuration.
- **RDBMS_Profile**: A Spring profile (`mysql`, `postgres`, `oracle`) that activates the corresponding relational database driver and JPA dialect for tenant metadata and risk rule storage.
- **Swagger_UI**: The OpenAPI 3.0 interactive documentation UI exposed by each service at `/swagger-ui.html`.

---

## Requirements

### Requirement 1: Multi-Module Maven Project Structure

**User Story:** As a platform engineer, I want the entire AUDITX codebase organized as a Maven multi-module monorepo, so that each service can be built, tested, and deployed independently while sharing common dependencies.

#### Acceptance Criteria

1. THE AUDITX SHALL be structured as a Maven multi-module project with a root `pom.xml` that declares the following modules: `auditx-gateway`, `auditx-ingestion`, `auditx-parser`, `auditx-risk-engine`, `auditx-llm`, `auditx-alert`, `auditx-report`, `auditx-sdk`, and `auditx-common`.
2. THE AUDITX SHALL compile all modules using Java 21 as the source and target version, with virtual threads enabled via `spring.threads.virtual.enabled=true` in each service's `application.yml`.
3. THE `auditx-common` module SHALL contain shared DTOs, exception types, Kafka topic constants, and utility classes used by two or more other modules.
4. WHEN a module is built with `mvn package`, THE AUDITX SHALL produce a self-contained executable JAR for each service module.

---

### Requirement 2: API Gateway Service

**User Story:** As a platform operator, I want a single entry point that routes requests to downstream services and enforces JWT authentication, so that clients interact with one stable host and unauthorized requests are rejected before reaching internal services.

#### Acceptance Criteria

1. THE API_Gateway SHALL route inbound HTTP requests to the correct downstream service based on path prefix configuration.
2. WHEN a request arrives at a protected route, THE API_Gateway SHALL validate the JWT signature and expiry before forwarding the request.
3. IF a JWT is missing, expired, or has an invalid signature, THEN THE API_Gateway SHALL return HTTP 401 with a structured JSON error body.
4. THE API_Gateway SHALL forward the validated `tenantId` claim from the JWT as an `X-Tenant-Id` HTTP header to downstream services.
5. THE API_Gateway SHALL expose a health check endpoint at `GET /actuator/health` that returns HTTP 200 when the gateway is operational.
6. WHERE rate limiting is enabled for a tenant, THE API_Gateway SHALL enforce the per-tenant request rate limit using the Rate_Limiter before routing the request.
7. IF a tenant exceeds the configured rate limit, THEN THE API_Gateway SHALL return HTTP 429 with a `Retry-After` header indicating when the limit resets.

---

### Requirement 3: Audit Ingestion Service

**User Story:** As a client application developer, I want to POST raw logs and structured events to a REST endpoint, so that my application's identity activity is captured in AUDITX for analysis.

#### Acceptance Criteria

1. THE Ingestion_Service SHALL expose a REST endpoint at `POST /api/events/raw` that accepts a JSON body conforming to the raw event schema.
2. WHEN a request is received at `POST /api/events/raw`, THE Ingestion_Service SHALL validate the `X-API-Key` header against the tenant's stored API_Key before processing the payload.
3. IF the `X-API-Key` header is absent or does not match a known tenant, THEN THE Ingestion_Service SHALL return HTTP 401 and SHALL NOT publish the event to Kafka.
4. WHEN a valid event is received, THE Ingestion_Service SHALL publish the event payload to the `raw-events` Kafka_Topic within 500ms of receiving the request.
5. THE Ingestion_Service SHALL return HTTP 202 with an `eventId` in the response body after successfully publishing to Kafka.
6. WHEN a request includes an `idempotencyKey` field, THE Ingestion_Service SHALL check Redis for a prior submission with the same key and tenantId combination; IF a duplicate is found, THEN THE Ingestion_Service SHALL return HTTP 200 with the original `eventId` without re-publishing to Kafka.
7. IF the Kafka broker is unavailable, THEN THE Ingestion_Service SHALL return HTTP 503 and SHALL log the failure with structured JSON including `tenantId`, `timestamp`, and error details.
8. THE Ingestion_Service SHALL accept both unstructured string payloads (Raw_Log) and pre-structured JSON payloads in the same endpoint, distinguished by a `payloadType` field with values `RAW` or `STRUCTURED`.
9. WHILE the Rate_Limiter indicates a tenant has exceeded its ingestion quota, THE Ingestion_Service SHALL return HTTP 429 and SHALL NOT publish to Kafka.

---

### Requirement 4: Parser Service

**User Story:** As a data analyst, I want raw logs automatically converted to structured JSON, so that I can query and analyze identity events without writing custom parsers for each log format.

#### Acceptance Criteria

1. THE Parser_Service SHALL consume messages from the `raw-events` Kafka_Topic using a consumer group named `parser-group`.
2. WHEN a Raw_Log message is consumed, THE Parser_Service SHALL apply a regex-based parsing strategy to extract fields including `timestamp`, `userId`, `action`, `sourceIp`, `tenantId`, and `outcome`.
3. THE Parser_Service SHALL support an extensible parsing strategy interface so that additional parsing strategies can be registered without modifying existing code.
4. WHEN a Raw_Log is successfully parsed, THE Parser_Service SHALL store the resulting Structured_Event in the `audit_events` MongoDB collection with the `tenantId` field populated.
5. WHEN a Raw_Log is successfully parsed, THE Parser_Service SHALL publish the Structured_Event to the `structured-events` Kafka_Topic.
6. IF a Raw_Log cannot be parsed by any registered strategy, THEN THE Parser_Service SHALL store the original payload in the `raw_logs` MongoDB collection with a `parseStatus` of `FAILED` and SHALL NOT publish to the `structured-events` topic.
7. THE Parser_Service SHALL process each message idempotently using the event's `eventId` as a deduplication key stored in MongoDB.
8. THE Parser_Service SHALL produce structured JSON logs for every parse attempt, including `eventId`, `tenantId`, `strategy`, `parseStatus`, and `durationMs`.

---

### Requirement 5: Risk Engine Service

**User Story:** As a security analyst, I want the platform to automatically score the risk of identity events using configurable rules, so that high-risk activity is surfaced without manual log review.

#### Acceptance Criteria

1. THE Risk_Engine SHALL consume messages from the `structured-events` Kafka_Topic using a consumer group named `risk-engine-group`.
2. WHEN a Structured_Event is consumed, THE Risk_Engine SHALL load the active Risk_Rules for the event's `tenantId` from MongoDB.
3. THE Risk_Engine SHALL compute a Risk_Score between 0 and 100 for each event by summing the weights of all matching Risk_Rules.
4. THE Risk_Engine SHALL evaluate a "failed login threshold" rule: WHEN the number of failed login events for a `userId` within a configurable rolling time window exceeds a configurable count threshold, THE Risk_Engine SHALL assign the maximum rule weight to that rule for the triggering event.
5. THE Risk_Engine SHALL evaluate a "geo-location anomaly" rule: WHEN a login event originates from a country not present in the `userId`'s recent login history within a configurable lookback period, THE Risk_Engine SHALL assign the configured rule weight.
6. WHEN a Risk_Score is computed, THE Risk_Engine SHALL store the score alongside the `eventId`, `tenantId`, `userId`, `ruleMatches`, and `computedAt` timestamp in the `audit_events` collection by updating the corresponding document.
7. WHEN a computed Risk_Score exceeds the tenant's configured alert threshold, THE Risk_Engine SHALL publish an Alert message to the `alerts` Kafka_Topic.
8. IF the Risk_Rules collection for a tenant is empty, THEN THE Risk_Engine SHALL assign a Risk_Score of 0 and SHALL NOT publish an Alert.
9. THE Risk_Engine SHALL process each event idempotently; WHEN an event with a previously computed Risk_Score is received, THE Risk_Engine SHALL skip reprocessing.

---

### Requirement 6: LLM Intelligence Service

**User Story:** As a security analyst, I want AI-generated explanations of incidents and the ability to query audit data using natural language, so that I can investigate threats faster without writing complex database queries.

#### Acceptance Criteria

1. THE LLM_Service SHALL integrate with an LLM provider configured via `application.yml`, supporting both OpenAI and Azure OpenAI as selectable providers.
2. THE LLM_Service SHALL expose a REST endpoint at `POST /api/llm/explain` that accepts an `eventId` and `tenantId`, retrieves the corresponding Structured_Event, and returns a natural-language incident explanation generated by the LLM provider.
3. THE LLM_Service SHALL expose a REST endpoint at `POST /api/llm/query` that accepts a natural-language question string and `tenantId`, translates it to a MongoDB query using a prompt template, executes the query against the `audit_events` collection scoped to the `tenantId`, and returns the results.
4. THE LLM_Service SHALL expose a REST endpoint at `POST /api/llm/summarize` that accepts a list of Alert identifiers and `tenantId` and returns a concise natural-language summary of the alerts generated by the LLM provider.
5. THE LLM_Service SHALL use versioned prompt templates stored as classpath resources, with one template per feature (explain, query, summarize).
6. WHEN an LLM provider call fails, THE LLM_Service SHALL retry the call up to 3 times with exponential backoff starting at 500ms.
7. IF all retry attempts are exhausted, THEN THE LLM_Service SHALL return a fallback response indicating the service is temporarily unavailable, and SHALL log the failure with structured JSON including `tenantId`, `feature`, `attemptCount`, and `errorMessage`.
8. THE LLM_Service SHALL enforce that all MongoDB queries generated from natural language are scoped to the requesting tenant's `tenantId` before execution.

---

### Requirement 7: Alert Service

**User Story:** As a security operations engineer, I want alerts dispatched automatically via webhook and email when risk thresholds are exceeded, so that the team is notified in real time without polling the platform.

#### Acceptance Criteria

1. THE Alert_Service SHALL consume messages from the `alerts` Kafka_Topic using a consumer group named `alert-service-group`.
2. WHEN an Alert message is consumed, THE Alert_Service SHALL store the alert in the `alerts` MongoDB collection with fields: `alertId`, `tenantId`, `eventId`, `riskScore`, `ruleMatches`, `status`, and `createdAt`.
3. WHEN an Alert is stored, THE Alert_Service SHALL dispatch a webhook notification by sending an HTTP POST to the tenant's configured webhook URL with the alert payload as JSON.
4. WHEN an Alert is stored, THE Alert_Service SHALL dispatch an email notification to the tenant's configured alert email address using a mock email implementation that logs the email content as structured JSON.
5. IF a webhook delivery fails after 3 retry attempts with exponential backoff, THEN THE Alert_Service SHALL update the alert's `status` to `WEBHOOK_FAILED` in MongoDB and SHALL log the failure.
6. THE Alert_Service SHALL process each Alert idempotently using the `alertId` as a deduplication key.
7. THE Alert_Service SHALL support per-tenant notification channel configuration, allowing a tenant to enable or disable webhook and email notifications independently.

---

### Requirement 8: Report Service

**User Story:** As a compliance officer, I want to generate PDF compliance reports for a specified time range and tenant, so that I can submit audit evidence to regulators without manually exporting data.

#### Acceptance Criteria

1. THE Report_Service SHALL expose a REST endpoint at `POST /api/reports/generate` that accepts `tenantId`, `startDate`, `endDate`, and `reportType` parameters.
2. WHEN a report generation request is received, THE Report_Service SHALL query the `audit_events` and `alerts` MongoDB collections scoped to the `tenantId` and the specified date range.
3. THE Report_Service SHALL produce a PDF report containing: a summary of total events, a breakdown of events by `action` type, a list of high-risk events (Risk_Score ≥ 70), and a list of alerts within the date range.
4. THE Report_Service SHALL be designed with a Puppeteer-compatible HTML rendering interface, so that a Puppeteer-based PDF renderer can be substituted without changing the report data assembly logic.
5. WHEN a report is generated, THE Report_Service SHALL return the PDF as a binary response with `Content-Type: application/pdf` and a `Content-Disposition: attachment` header.
6. IF the requested date range contains no events for the tenant, THEN THE Report_Service SHALL generate a report stating that no events were found for the specified period rather than returning an error.

---

### Requirement 9: Multi-Tenancy and Data Isolation

**User Story:** As a platform operator, I want all data strictly isolated by tenant, so that one tenant cannot access or influence another tenant's data under any circumstances.

#### Acceptance Criteria

1. THE AUDITX SHALL include a `tenantId` field in every document stored in the `audit_events`, `raw_logs`, `tenants`, `risk_rules`, and `alerts` MongoDB collections.
2. WHEN any service queries MongoDB, THE AUDITX SHALL include the `tenantId` as a mandatory filter condition on every query.
3. THE AUDITX SHALL create a MongoDB index on `tenantId` for each collection to ensure query performance is not degraded by tenant-scoped filtering.
4. IF a request reaches a service without a resolvable `tenantId`, THEN THE AUDITX SHALL reject the request with HTTP 400 and SHALL NOT execute any database operation.
5. THE AUDITX SHALL store tenant configuration — including API_Key hash, alert thresholds, webhook URL, and notification preferences — in the `tenants` collection, scoped by `tenantId`.

---

### Requirement 10: AUDITX SDK

**User Story:** As a Java developer integrating a Spring Boot application with AUDITX, I want a drop-in SDK that auto-captures HTTP requests and login events, so that I can start sending audit data with minimal configuration.

#### Acceptance Criteria

1. THE SDK SHALL be distributed as a Maven dependency that Spring Boot applications include in their `pom.xml`.
2. WHEN the SDK dependency is present on the classpath and `auditx.enabled=true` is set in `application.yml`, THE SDK SHALL auto-configure itself using Spring Boot auto-configuration without requiring additional Java code from the integrating application.
3. THE SDK SHALL accept the following configuration properties via `application.yml`: `auditx.endpoint`, `auditx.api-key`, `auditx.tenant-id`, `auditx.async`, and `auditx.retry.max-attempts`.
4. WHEN `auditx.async=true`, THE SDK SHALL send events to the Ingestion_Service asynchronously on a dedicated thread pool without blocking the calling thread.
5. THE SDK SHALL auto-capture outbound HTTP request events by registering a Spring `HandlerInterceptor` that records `method`, `path`, `statusCode`, `userId` (from security context), `tenantId`, and `durationMs` for each request.
6. THE SDK SHALL provide a `AuditxLoginEventPublisher` bean that integrating applications call to explicitly publish login events with fields: `userId`, `outcome` (`SUCCESS` or `FAILURE`), `sourceIp`, and `timestamp`.
7. WHEN an event send fails, THE SDK SHALL retry the send up to the configured `auditx.retry.max-attempts` value using exponential backoff, and SHALL log each retry attempt.
8. IF all retry attempts are exhausted, THE SDK SHALL log the failure as a structured JSON error and SHALL NOT throw an exception to the calling application.
9. THE SDK SHALL generate a unique `idempotencyKey` per event before sending, so that transient network failures that cause retries do not result in duplicate events in AUDITX.

---

### Requirement 11: Security — API Key and JWT Authentication

**User Story:** As a platform security engineer, I want all ingestion endpoints protected by API key and all dashboard endpoints protected by JWT, so that unauthorized access to tenant data is prevented.

#### Acceptance Criteria

1. THE Ingestion_Service SHALL validate the `X-API-Key` header on every request to `POST /api/events/raw` by comparing a SHA-256 hash of the provided key against the stored hash in the `tenants` collection.
2. THE API_Gateway SHALL validate JWT tokens using a configurable public key or JWKS endpoint specified in `application.yml`.
3. WHEN a JWT is valid, THE API_Gateway SHALL extract the `tenantId`, `sub` (userId), and `roles` claims and forward them as HTTP headers to downstream services.
4. THE AUDITX SHALL store API keys as SHA-256 hashes in MongoDB and SHALL NOT store plaintext API keys anywhere in the system.
5. THE Rate_Limiter SHALL use Redis to track request counts per `tenantId` within a configurable sliding window, and SHALL enforce a configurable maximum request count per window.

---

### Requirement 12: Observability and Structured Logging with MDC

**User Story:** As a platform operator, I want all services to emit structured JSON logs enriched with MDC context fields and expose health and metrics endpoints, so that I can correlate log entries across services using a single traceId and filter by tenant without post-processing.

#### Acceptance Criteria

1. THE AUDITX SHALL configure all service loggers to emit JSON-formatted log entries using Logback with `logstash-logback-encoder`, including the following fields in every log line: `timestamp`, `level`, `service`, `traceId`, `tenantId`, `userId`, `eventId`, and `message`.
2. THE AUDITX SHALL populate MDC keys at every request and message entry point before any business logic executes:
   - HTTP services: a `MdcFilter` (servlet filter or WebFlux `WebFilter`) SHALL set `traceId`, `tenantId`, and `userId` into MDC on every inbound request and clear them in a `finally` block after the response is sent.
   - Kafka consumers: a shared `MdcKafkaConsumerInterceptor` SHALL extract `traceId` and `tenantId` from Kafka message headers and populate MDC before the listener method is invoked, clearing MDC after the listener returns.
3. THE `traceId` MDC value SHALL be sourced from the inbound `X-Trace-Id` HTTP header if present; otherwise a new UUID SHALL be generated and set as the `traceId` for that request chain.
4. THE AUDITX SHALL propagate MDC context across async boundaries: WHEN an event is published to Kafka, THE AUDITX SHALL write the current MDC `traceId` and `tenantId` values as Kafka message headers so downstream consumers can restore the same MDC context.
5. THE AUDITX SHALL propagate the `X-Trace-Id` header on all outbound `WebClient` calls by reading the current MDC `traceId` value and adding it as a request header.
6. THE AUDITX SHALL expose Spring Boot Actuator endpoints for health (`/actuator/health`) and metrics (`/actuator/metrics`) on each service.
7. WHEN a Kafka consumer fails to process a message after the configured retry attempts, THE AUDITX SHALL log the failure at ERROR level with MDC fields populated and the log message body containing `topic`, `consumerGroup`, `attemptCount`, and `errorMessage`.
8. THE AUDITX SHALL provide a shared `MdcUtil` class in `auditx-common` with static helper methods: `setRequestContext(traceId, tenantId, userId)`, `setEventContext(traceId, tenantId, eventId)`, and `clear()`, so that all services use a consistent MDC key naming convention.
9. WHEN `tenantId` or `userId` is not available in a given context (e.g., unauthenticated health check), THE AUDITX SHALL set those MDC keys to the literal string `"unknown"` rather than omitting them, ensuring every log line has a consistent field set.
10. THE AUDITX SHALL configure the `logback-spring.xml` in each service to include a `<springProperty>` that injects the Spring application name as the `service` field in every log entry, so log aggregators can identify the source service without parsing the message.

---

### Requirement 13: Testing

**User Story:** As a quality engineer, I want comprehensive automated tests across all service modules, so that regressions are caught before deployment and the platform maintains at least 70% code coverage.

#### Acceptance Criteria

1. THE AUDITX SHALL include JUnit 5 and Mockito unit tests for all service-layer classes in every module, achieving a minimum of 70% line coverage per module as measured by JaCoCo.
2. THE AUDITX SHALL include Spring Boot integration tests for each service using Embedded MongoDB and Kafka Testcontainers to validate end-to-end flows within each service boundary.
3. THE AUDITX SHALL include RestAssured API tests for all REST endpoints in the Ingestion_Service, LLM_Service, Report_Service, and API_Gateway, covering both success and error response scenarios.
4. THE SDK SHALL include unit tests that validate: event serialization, successful event sending, retry behavior on HTTP 5xx responses, and the idempotency key generation uniqueness property.
5. WHEN the SDK retry mechanism is tested, THE SDK test SHALL verify that after exhausting all retry attempts the SDK logs a structured error and does not propagate an exception to the caller.
6. THE AUDITX SHALL include an integration test that validates the full ingestion-to-parse pipeline: a raw event POSTed to the Ingestion_Service SHALL appear as a Structured_Event in MongoDB after the Parser_Service processes it.

---

### Requirement 14: DevOps — Docker and Docker Compose

**User Story:** As a developer, I want to run the entire AUDITX platform locally with a single command, so that I can develop and test against a realistic environment without manual infrastructure setup.

#### Acceptance Criteria

1. THE AUDITX SHALL provide a `Dockerfile` for each service module that produces a minimal JRE-based container image using a multi-stage build.
2. THE AUDITX SHALL provide a `docker-compose.yml` at the repository root that starts all services together with MongoDB, Redis, Kafka, and ZooKeeper.
3. WHEN `docker-compose up` is executed, THE AUDITX SHALL start all services with environment-specific configuration injected via environment variables defined in a `.env` file.
4. THE AUDITX SHALL configure inter-service communication in `docker-compose.yml` using Docker internal DNS names so that no hardcoded IP addresses are required.
5. THE AUDITX SHALL expose each service on a distinct host port in `docker-compose.yml` to allow local development tools to reach individual services directly.
6. THE `docker-compose.yml` SHALL include health check definitions for MongoDB, Redis, and Kafka so that dependent services wait for infrastructure readiness before starting.

---

### Requirement 15: Cloud and On-Premises Deployment Support

**User Story:** As a platform operator, I want to deploy AUDITX on both cloud infrastructure and on-premises servers, so that enterprise customers with data residency requirements can run the platform in their own data centers.

#### Acceptance Criteria

1. THE AUDITX SHALL support a `cloud` Spring profile that configures services to connect to managed cloud infrastructure (MongoDB Atlas, AWS MSK, ElastiCache, or equivalent).
2. THE AUDITX SHALL support an `onprem` Spring profile that configures services to connect to locally hosted infrastructure (self-managed MongoDB, Kafka, Redis).
3. WHEN a service starts with the `cloud` profile active, THE AUDITX SHALL load `application-cloud.yml` for cloud-specific connection strings and TLS settings.
4. WHEN a service starts with the `onprem` profile active, THE AUDITX SHALL load `application-onprem.yml` for on-premises connection strings without mandatory TLS.
5. THE `docker-compose.yml` SHALL default to the `onprem` profile so that the full platform runs locally without cloud credentials.
6. THE AUDITX SHALL document the required environment variables for each deployment profile in a `DEPLOYMENT.md` file at the repository root.

---

### Requirement 16: Relational Database Support via Spring Profiles

**User Story:** As a platform operator deploying AUDITX at an enterprise that mandates a relational database, I want to store tenant metadata and risk rules in MySQL, PostgreSQL, or Oracle, so that AUDITX integrates with existing enterprise database infrastructure.

#### Acceptance Criteria

1. THE AUDITX SHALL support three RDBMS Spring profiles: `mysql`, `postgres`, and `oracle`, each activating the corresponding JDBC driver, Spring Data JPA dialect, and Flyway migration scripts.
2. WHEN the `mysql` profile is active, THE AUDITX SHALL use `spring-boot-starter-data-jpa` with the MySQL Connector/J driver and configure the `tenants` and `risk_rules` data as relational tables.
3. WHEN the `postgres` profile is active, THE AUDITX SHALL use the PostgreSQL JDBC driver and configure the same relational schema.
4. WHEN the `oracle` profile is active, THE AUDITX SHALL use the Oracle JDBC driver (ojdbc11) and configure the same relational schema with Oracle-compatible DDL.
5. THE AUDITX SHALL provide Flyway migration scripts (`V1__init.sql`) for each RDBMS profile under `src/main/resources/db/migration/{mysql,postgres,oracle}/`.
6. WHEN no RDBMS profile is active, THE AUDITX SHALL default to MongoDB for all storage, preserving full backward compatibility.
7. THE AUDITX SHALL abstract all database access behind repository interfaces so that switching profiles requires only configuration changes, not code changes.
8. THE `docker-compose.yml` SHALL include optional service definitions for MySQL and PostgreSQL that can be enabled by setting the corresponding profile, with no Oracle container (Oracle requires a licensed image).

---

### Requirement 17: Swagger / OpenAPI 3.0 Documentation

**User Story:** As an API consumer or integration developer, I want interactive Swagger documentation for every AUDITX REST service, so that I can explore, understand, and test the APIs without reading source code.

#### Acceptance Criteria

1. THE AUDITX SHALL include `springdoc-openapi-starter-webmvc-ui` (or `webflux-ui` for reactive services) in every service module that exposes REST endpoints.
2. EACH service SHALL expose Swagger UI at `GET /swagger-ui.html` and the OpenAPI JSON spec at `GET /v3/api-docs`.
3. THE AUDITX SHALL annotate all controller methods with `@Operation`, `@ApiResponse`, and `@Parameter` annotations describing the endpoint purpose, request parameters, and all possible HTTP response codes.
4. THE AUDITX SHALL group API operations by service tag (e.g., `Ingestion`, `LLM`, `Report`, `Alert`) in the Swagger UI.
5. THE Swagger UI SHALL be accessible without authentication in local/development environments and SHALL be disabled or secured behind JWT in production profiles.
6. THE API_Gateway SHALL expose an aggregated Swagger UI at `GET /swagger-ui.html` that combines the OpenAPI specs from all downstream services using SpringDoc's multi-service aggregation support.
