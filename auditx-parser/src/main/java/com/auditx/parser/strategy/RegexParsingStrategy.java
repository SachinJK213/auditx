package com.auditx.parser.strategy;

import com.auditx.common.dto.StructuredEventDto;
import com.auditx.common.util.IdempotencyKeyGenerator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Order(10)
@Component
public class RegexParsingStrategy implements ParsingStrategy {

    private static final Pattern PATTERN = Pattern.compile(
            "timestamp=(?<timestamp>\\S+).*userId=(?<userId>\\S+).*action=(?<action>\\S+)" +
            ".*sourceIp=(?<sourceIp>\\S+).*tenantId=(?<tenantId>\\S+).*outcome=(?<outcome>\\S+)"
    );

    @Override
    public boolean supports(String payload) {
        if (payload == null) return false;
        return PATTERN.matcher(payload).find();
    }

    @Override
    public StructuredEventDto parse(String payload) {
        Matcher m = PATTERN.matcher(payload);
        if (!m.find()) {
            throw new IllegalArgumentException("Payload does not match regex pattern: " + payload);
        }

        String rawTimestamp = m.group("timestamp");
        Instant timestamp;
        try {
            timestamp = Instant.parse(rawTimestamp);
        } catch (Exception e) {
            timestamp = Instant.now();
        }

        return new StructuredEventDto(
                IdempotencyKeyGenerator.generate(),
                m.group("tenantId"),
                m.group("userId"),
                m.group("action"),
                m.group("sourceIp"),
                m.group("outcome"),
                timestamp,
                null,
                null,
                null
        );
    }
}
