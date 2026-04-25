package com.auditx.parser.strategy;

import com.auditx.common.dto.StructuredEventDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class RegexParsingStrategyTest {

    private RegexParsingStrategy strategy;

    @BeforeEach
    void setUp() { strategy = new RegexParsingStrategy(); }

    @Test
    void supports_validKeyValuePayload_returnsTrue() {
        String p = "timestamp=2024-06-01T10:00:00Z userId=alice action=LOGIN sourceIp=192.168.1.1 tenantId=t1 outcome=SUCCESS";
        assertThat(strategy.supports(p)).isTrue();
    }

    @Test
    void supports_jsonPayload_returnsFalse() {
        assertThat(strategy.supports("{\"userId\":\"alice\"}")).isFalse();
    }

    @Test
    void supports_nullPayload_returnsFalse() {
        assertThat(strategy.supports(null)).isFalse();
    }

    @Test
    void supports_emptyPayload_returnsFalse() {
        assertThat(strategy.supports("")).isFalse();
    }

    @Test
    void supports_partialPayload_returnsFalse() {
        assertThat(strategy.supports("userId=alice action=LOGIN")).isFalse();
    }

    @Test
    void parse_validPayload_extractsAllFields() {
        String p = "timestamp=2024-06-01T10:00:00Z userId=alice action=LOGIN sourceIp=192.168.1.1 tenantId=t1 outcome=SUCCESS";
        StructuredEventDto dto = strategy.parse(p);

        assertThat(dto.userId()).isEqualTo("alice");
        assertThat(dto.action()).isEqualTo("LOGIN");
        assertThat(dto.sourceIp()).isEqualTo("192.168.1.1");
        assertThat(dto.tenantId()).isEqualTo("t1");
        assertThat(dto.outcome()).isEqualTo("SUCCESS");
        assertThat(dto.timestamp()).isEqualTo(Instant.parse("2024-06-01T10:00:00Z"));
        assertThat(dto.eventId()).isNotBlank();
    }

    @Test
    void parse_invalidTimestamp_fallsBackToNow() {
        String p = "timestamp=NOT_A_TIMESTAMP userId=bob action=LOGOUT sourceIp=10.0.0.1 tenantId=t1 outcome=SUCCESS";
        Instant before = Instant.now().minusSeconds(2);
        StructuredEventDto dto = strategy.parse(p);
        assertThat(dto.timestamp()).isAfter(before);
    }

    @Test
    void parse_eachCallGeneratesUniqueEventId() {
        String p = "timestamp=2024-06-01T10:00:00Z userId=carol action=LOGIN sourceIp=10.0.0.5 tenantId=t1 outcome=FAILURE";
        StructuredEventDto a = strategy.parse(p);
        StructuredEventDto b = strategy.parse(p);
        assertThat(a.eventId()).isNotEqualTo(b.eventId());
    }

    @Test
    void parse_doesNotMatchPayload_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> strategy.parse("garbage payload"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
