package com.auditx.parser.strategy;

import com.auditx.common.dto.StructuredEventDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class SyslogParsingStrategyTest {

    private SyslogParsingStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new SyslogParsingStrategy();
    }

    @Test
    void supports_syslogWithPriority_returnsTrue() {
        assertThat(strategy.supports("<34>Oct 11 22:14:15 mymachine su: 'su root' failed")).isTrue();
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
    void parse_keyValueMessage_extractsFields() {
        String syslog = "<34>userId=alice action=LOGIN outcome=SUCCESS sourceIp=10.0.0.1 tenantId=tenant-demo";
        StructuredEventDto dto = strategy.parse(syslog);

        assertThat(dto.userId()).isEqualTo("alice");
        assertThat(dto.action()).isEqualTo("LOGIN");
        assertThat(dto.outcome()).isEqualTo("SUCCESS");
        assertThat(dto.sourceIp()).isEqualTo("10.0.0.1");
        assertThat(dto.tenantId()).isEqualTo("tenant-demo");
        assertThat(dto.eventId()).isNotBlank();
        assertThat(dto.timestamp()).isNotNull();
    }

    @Test
    void parse_rfc3164Message_extractsHostnameAndApp() {
        String syslog = "<34>Oct 11 22:14:15 mymachine su: 'su root' failed";
        StructuredEventDto dto = strategy.parse(syslog);

        assertThat(dto.sourceIp()).isEqualTo("mymachine");
        assertThat(dto.action()).isEqualTo("su");
        assertThat(dto.outcome()).isEqualTo("UNKNOWN");
        assertThat(dto.eventId()).isNotBlank();
        assertThat(dto.timestamp()).isNotNull();
    }

    @Test
    void parse_generatesUniqueEventId() {
        String syslog = "<34>userId=alice action=LOGIN outcome=SUCCESS sourceIp=10.0.0.1 tenantId=tenant-demo";
        StructuredEventDto first  = strategy.parse(syslog);
        StructuredEventDto second = strategy.parse(syslog);

        assertThat(first.eventId()).isNotEqualTo(second.eventId());
    }
}
