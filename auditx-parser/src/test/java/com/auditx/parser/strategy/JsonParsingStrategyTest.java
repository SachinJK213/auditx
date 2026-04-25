package com.auditx.parser.strategy;

import com.auditx.common.dto.StructuredEventDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class JsonParsingStrategyTest {

    private JsonParsingStrategy strategy;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        strategy = new JsonParsingStrategy(objectMapper);
    }

    @Test
    void supports_validJson_returnsTrue() {
        assertThat(strategy.supports("{\"userId\":\"alice\"}")).isTrue();
    }

    @Test
    void supports_rawPayload_returnsFalse() {
        assertThat(strategy.supports("timestamp=2024-01-01T00:00:00Z userId=alice")).isFalse();
    }

    @Test
    void supports_null_returnsFalse() {
        assertThat(strategy.supports(null)).isFalse();
    }

    @Test
    void supports_notClosedBrace_returnsFalse() {
        // Starts with { but does not end with }
        assertThat(strategy.supports("{")).isFalse();
    }

    @Test
    void parse_allFields_extractedCorrectly() {
        String json = "{\"userId\":\"alice\",\"action\":\"LOGIN\",\"sourceIp\":\"10.0.0.1\"," +
                "\"outcome\":\"SUCCESS\",\"tenantId\":\"tenant-demo\",\"timestamp\":\"2024-06-01T10:00:00Z\"}";
        StructuredEventDto dto = strategy.parse(json);

        assertThat(dto.userId()).isEqualTo("alice");
        assertThat(dto.action()).isEqualTo("LOGIN");
        assertThat(dto.sourceIp()).isEqualTo("10.0.0.1");
        assertThat(dto.tenantId()).isEqualTo("tenant-demo");
        assertThat(dto.outcome()).isEqualTo("SUCCESS");
        assertThat(dto.timestamp()).isNotNull();
        assertThat(dto.timestamp()).isEqualTo(Instant.parse("2024-06-01T10:00:00Z"));
    }

    @Test
    void parse_missingTimestamp_fallsBackToNow() {
        String json = "{\"userId\":\"alice\",\"action\":\"LOGIN\"}";
        Instant before = Instant.now().minusSeconds(2);
        StructuredEventDto dto = strategy.parse(json);

        assertThat(dto.timestamp()).isNotNull();
        assertThat(dto.timestamp()).isAfter(before);
    }

    @Test
    void parse_missingSourceIp_defaultsToZero() {
        String json = "{\"userId\":\"alice\",\"action\":\"LOGIN\"}";
        StructuredEventDto dto = strategy.parse(json);

        assertThat(dto.sourceIp()).isEqualTo("0.0.0.0");
    }

    @Test
    void parse_invalidJson_throwsIllegalArgument() {
        // Starts with { and ends with } so supports() would pass, but content is invalid JSON
        assertThatThrownBy(() -> strategy.parse("{ \"a\": }"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
