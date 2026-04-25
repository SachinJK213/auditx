package com.auditx.parser.strategy;

import com.auditx.common.dto.StructuredEventDto;
import com.auditx.common.util.IdempotencyKeyGenerator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Order(2)
@Component
public class CsvParsingStrategy implements ParsingStrategy {

    // Fixed column order: timestamp,userId,action,sourceIp,tenantId,outcome
    private static final int MIN_FIELDS = 6;

    @Override
    public boolean supports(String payload) {
        if (payload == null) return false;
        String[] fields = payload.split(",", -1);
        if (fields.length < MIN_FIELDS) return false;
        // First field must parse as Instant — this rejects header lines like "timestamp,..."
        try {
            Instant.parse(fields[0].trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public StructuredEventDto parse(String payload) {
        String[] fields = payload.split(",", -1);
        if (fields.length < MIN_FIELDS) {
            throw new IllegalArgumentException(
                    "CSV payload must have at least " + MIN_FIELDS + " fields, got: " + fields.length);
        }

        Instant timestamp;
        try {
            timestamp = Instant.parse(fields[0].trim());
        } catch (Exception e) {
            timestamp = Instant.now();
        }

        String userId = fields[1].trim();
        String action = fields[2].trim();
        String sourceIp = fields[3].trim();
        String tenantId = fields[4].trim();
        String outcome = fields[5].trim();

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
    }
}
