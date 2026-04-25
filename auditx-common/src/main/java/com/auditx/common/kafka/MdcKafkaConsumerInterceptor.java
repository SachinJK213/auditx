package com.auditx.common.kafka;

import com.auditx.common.util.MdcUtil;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka ConsumerInterceptor that extracts traceId and tenantId from message headers
 * and populates MDC before the listener method executes.
 *
 * <p>Register in KafkaConfig consumer props:
 * <pre>
 *   props.put(ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG, MdcKafkaConsumerInterceptor.class.getName());
 * </pre>
 */
public class MdcKafkaConsumerInterceptor<K, V> implements ConsumerInterceptor<K, V> {

    @Override
    public ConsumerRecords<K, V> onConsume(ConsumerRecords<K, V> records) {
        records.forEach(record -> {
            String traceId = extractHeader(record.headers().lastHeader(MdcUtil.TRACE_ID));
            String tenantId = extractHeader(record.headers().lastHeader(MdcUtil.TENANT_ID));

            if (traceId == null || traceId.isBlank()) {
                traceId = UUID.randomUUID().toString();
            }

            MdcUtil.setEventContext(traceId, tenantId, null);
        });
        return records;
    }

    @Override
    public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {
        // no-op
    }

    @Override
    public void close() {
        MdcUtil.clear();
    }

    @Override
    public void configure(Map<String, ?> configs) {
        // no-op
    }

    private String extractHeader(Header header) {
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
