package com.auditx.parser.strategy;

import com.auditx.common.dto.StructuredEventDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class CsvParsingStrategyTest {

    private CsvParsingStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new CsvParsingStrategy();
    }

    @Test
    void supports_validCsvLine_returnsTrue() {
        String csv = "2024-01-01T00:00:00Z,alice,LOGIN,10.0.0.1,tenant-demo,SUCCESS";
        assertThat(strategy.supports(csv)).isTrue();
    }

    @Test
    void supports_headerLine_returnsFalse() {
        // First field "timestamp" cannot be parsed as Instant
        assertThat(strategy.supports("timestamp,userId,action,sourceIp,tenantId,outcome")).isFalse();
    }

    @Test
    void supports_tooFewFields_returnsFalse() {
        assertThat(strategy.supports("2024-01-01T00:00:00Z,alice,LOGIN")).isFalse();
    }

    @Test
    void supports_jsonPayload_returnsFalse() {
        assertThat(strategy.supports("{\"userId\":\"alice\"}")).isFalse();
    }

    @Test
    void parse_extractsAllFields() {
        String csv = "2024-01-01T00:00:00Z,alice,LOGIN,10.0.0.1,tenant-demo,SUCCESS";
        StructuredEventDto dto = strategy.parse(csv);

        assertThat(dto.userId()).isEqualTo("alice");
        assertThat(dto.action()).isEqualTo("LOGIN");
        assertThat(dto.sourceIp()).isEqualTo("10.0.0.1");
        assertThat(dto.tenantId()).isEqualTo("tenant-demo");
        assertThat(dto.outcome()).isEqualTo("SUCCESS");
        assertThat(dto.timestamp()).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));
        assertThat(dto.eventId()).isNotBlank();
    }

    @Test
    void parse_invalidTimestamp_fallsBackToNow() {
        String csv = "not-a-date,alice,LOGIN,10.0.0.1,tenant-demo,SUCCESS";
        Instant before = Instant.now().minusSeconds(2);
        StructuredEventDto dto = strategy.parse(csv);

        assertThat(dto.timestamp()).isNotNull();
        assertThat(dto.timestamp()).isAfter(before);
    }

    @Test
    void parse_tooFewFields_throwsIllegalArgument() {
        assertThatThrownBy(() -> strategy.parse("2024-01-01T00:00:00Z,alice,LOGIN"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
