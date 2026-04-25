package com.auditx.parser.strategy;

import com.auditx.common.dto.StructuredEventDto;
import com.auditx.common.util.IdempotencyKeyGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Order(1)
@Component
public class JsonParsingStrategy implements ParsingStrategy {

    private final ObjectMapper objectMapper;

    public JsonParsingStrategy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String payload) {
        if (payload == null) return false;
        String trimmed = payload.trim();
        return trimmed.startsWith("{") && trimmed.endsWith("}");
    }

    @Override
    public StructuredEventDto parse(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);

            String userId = root.path("userId").asText(null);
            String action = root.path("action").asText(null);
            String sourceIp = root.path("sourceIp").isMissingNode() || root.path("sourceIp").isNull()
                    ? "0.0.0.0"
                    : root.path("sourceIp").asText("0.0.0.0");
            String outcome = root.path("outcome").isMissingNode() || root.path("outcome").isNull()
                    ? "UNKNOWN"
                    : root.path("outcome").asText("UNKNOWN");
            String tenantId = root.path("tenantId").asText(null);

            Instant timestamp;
            JsonNode tsNode = root.path("timestamp");
            if (tsNode.isMissingNode() || tsNode.isNull()) {
                timestamp = Instant.now();
            } else {
                try {
                    timestamp = Instant.parse(tsNode.asText());
                } catch (Exception e) {
                    timestamp = Instant.now();
                }
            }

            return new StructuredEventDto(
                    IdempotencyKeyGenerator.generate(),
                    tenantId,
                    userId,
                    action,
                    sourceIp,
                    outcome,
                    timestamp,
                    null,
                    null,
                    null
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse JSON payload: " + e.getMessage(), e);
        }
    }
}
