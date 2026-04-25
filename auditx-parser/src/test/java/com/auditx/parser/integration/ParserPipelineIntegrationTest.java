package com.auditx.parser.integration;

import com.auditx.common.dto.RawEventDto;
import com.auditx.common.enums.PayloadType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
class ParserPipelineIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @Container
    static MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        r.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
    }

    @Autowired ReactiveMongoTemplate mongoTemplate;

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void validKeyValuePayload_parsedAndStoredInMongoDB() throws Exception {
        String eventId = UUID.randomUUID().toString();
        RawEventDto raw = new RawEventDto(
                eventId, "t-integ",
                "timestamp=2024-06-01T10:00:00Z userId=alice action=LOGIN sourceIp=10.0.0.1 tenantId=t-integ outcome=SUCCESS",
                PayloadType.RAW, UUID.randomUUID().toString(), Instant.now());

        publishToKafka("raw-events", eventId, raw);

        // Wait for parser consumer to process
        Thread.sleep(5000);

        var doc = mongoTemplate.findOne(
                Query.query(Criteria.where("eventId").is(eventId)),
                Map.class, "audit_events").block(Duration.ofSeconds(5));

        assertThat(doc).isNotNull();
        assertThat(doc.get("userId")).isEqualTo("alice");
        assertThat(doc.get("action")).isEqualTo("LOGIN");
        assertThat(doc.get("parseStatus")).isEqualTo("SUCCESS");
    }

    @Test
    void unrecognisedPayload_storedInRawLogsAsFailed() throws Exception {
        String eventId = UUID.randomUUID().toString();
        RawEventDto raw = new RawEventDto(
                eventId, "t-integ", "totally unrecognised @@## format",
                PayloadType.RAW, UUID.randomUUID().toString(), Instant.now());

        publishToKafka("raw-events", eventId, raw);
        Thread.sleep(5000);

        var doc = mongoTemplate.findOne(
                Query.query(Criteria.where("eventId").is(eventId)),
                Map.class, "raw_logs").block(Duration.ofSeconds(5));

        assertThat(doc).isNotNull();
        assertThat(doc.get("parseStatus")).isEqualTo("FAILED");
    }

    @Test
    void validPayload_publishedToStructuredEventsTopic() throws Exception {
        String eventId = UUID.randomUUID().toString();
        RawEventDto raw = new RawEventDto(
                eventId, "t-integ",
                "timestamp=2024-06-01T12:00:00Z userId=bob action=LOGOUT sourceIp=192.168.1.5 tenantId=t-integ outcome=SUCCESS",
                PayloadType.RAW, UUID.randomUUID().toString(), Instant.now());

        publishToKafka("raw-events", eventId, raw);

        try (KafkaConsumer<String, String> consumer = kafkaConsumer()) {
            consumer.subscribe(List.of("structured-events"));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
            assertThat(records.count()).isGreaterThan(0);

            boolean found = false;
            for (var record : records) {
                if (record.value().contains(eventId)) { found = true; break; }
            }
            assertThat(found).as("eventId should appear in structured-events").isTrue();
        }
    }

    private void publishToKafka(String topic, String key, Object value) throws Exception {
        try (KafkaProducer<String, String> producer = kafkaProducer()) {
            producer.send(new ProducerRecord<>(topic, key, mapper.writeValueAsString(value))).get();
        }
    }

    private KafkaProducer<String, String> kafkaProducer() {
        return new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()));
    }

    private KafkaConsumer<String, String> kafkaConsumer() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"));
    }
}
