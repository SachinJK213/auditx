package com.auditx.livestream.config;

import com.auditx.common.dto.StructuredEventDto;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, StructuredEventDto> consumerFactory() {
        JsonDeserializer<StructuredEventDto> deserializer = new JsonDeserializer<>(StructuredEventDto.class);
        deserializer.addTrustedPackages("com.auditx.common.dto");
        deserializer.setUseTypeHeaders(false);

        return new DefaultKafkaConsumerFactory<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "live-stream-consumer",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true,
                // Fetch frequently for low latency
                ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1,
                ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 100,
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500
        ), new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, StructuredEventDto> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, StructuredEventDto> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(2);
        return factory;
    }
}
