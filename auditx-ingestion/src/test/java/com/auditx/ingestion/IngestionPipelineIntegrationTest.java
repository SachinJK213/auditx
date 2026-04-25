package com.auditx.ingestion;

import com.auditx.common.constants.KafkaTopics;
import com.auditx.common.enums.PayloadType;
import com.auditx.common.util.Sha256HashUtil;
import com.auditx.ingestion.model.TenantDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class IngestionPipelineIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @Container
    static MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> String.valueOf(redis.getMappedPort(6379)));
    }

    @Autowired private WebTestClient webTestClient;
    @Autowired private ReactiveMongoTemplate mongoTemplate;

    private static final String TEST_API_KEY = "test-api-key-" + UUID.randomUUID();
    private static final String TEST_TENANT_ID = "tenant-integration-test";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void insertTestTenant() {
        mongoTemplate.remove(Query.query(Criteria.where("tenantId").is(TEST_TENANT_ID)), TenantDocument.class).block();
        TenantDocument tenant = new TenantDocument();
        tenant.setTenantId(TEST_TENANT_ID);
        tenant.setApiKeyHash(Sha256HashUtil.hash(TEST_API_KEY));
        tenant.setAlertThreshold(80);
        tenant.setWebhookEnabled(false);
        tenant.setEmailEnabled(false);
        mongoTemplate.insert(tenant).block();
    }

    @Test
    void postRawEvent_shouldReturn202AndPublishToKafka() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        String body = objectMapper.writeValueAsString(Map.of(
                "payload", "timestamp=2024-01-15T10:00:00Z userId=alice action=LOGIN sourceIp=192.168.1.1 tenantId=" + TEST_TENANT_ID + " outcome=SUCCESS",
                "payloadType", PayloadType.RAW.name(),
                "idempotencyKey", idempotencyKey,
                "timestamp", Instant.now().toString()
        ));

        byte[] responseBytes = webTestClient.post().uri("/api/events/raw")
                .header("X-API-Key", TEST_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody().returnResult().getResponseBody();

        assertThat(responseBytes).isNotNull();
        JsonNode json = objectMapper.readTree(responseBytes);
        String eventId = json.path("eventId").asText();
        assertThat(eventId).isNotBlank();

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(KafkaTopics.RAW_EVENTS));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
            assertThat(records.count()).isGreaterThan(0);
        }
    }

    @Test
    void postDuplicateEvent_shouldReturn200WithSameEventId() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        String body = objectMapper.writeValueAsString(Map.of(
                "payload", "timestamp=2024-01-15T11:00:00Z userId=bob action=LOGOUT sourceIp=10.0.0.1 tenantId=" + TEST_TENANT_ID + " outcome=SUCCESS",
                "payloadType", PayloadType.RAW.name(),
                "idempotencyKey", idempotencyKey,
                "timestamp", Instant.now().toString()
        ));

        byte[] first = webTestClient.post().uri("/api/events/raw")
                .header("X-API-Key", TEST_API_KEY).contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                .exchange().expectStatus().isAccepted().expectBody().returnResult().getResponseBody();
        String originalEventId = objectMapper.readTree(first).path("eventId").asText();
        assertThat(originalEventId).isNotBlank();

        byte[] second = webTestClient.post().uri("/api/events/raw")
                .header("X-API-Key", TEST_API_KEY).contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                .exchange().expectStatus().isOk().expectBody().returnResult().getResponseBody();
        String duplicateEventId = objectMapper.readTree(second).path("eventId").asText();
        assertThat(duplicateEventId).isEqualTo(originalEventId);
    }

    @Test
    void postEventWithMissingApiKey_shouldReturn401() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "payload", "test", "payloadType", PayloadType.RAW.name(), "timestamp", Instant.now().toString()
        ));
        webTestClient.post().uri("/api/events/raw")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                .exchange().expectStatus().isUnauthorized();
    }
}
