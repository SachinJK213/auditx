package com.auditx.parser.strategy;

import com.auditx.common.dto.StructuredEventDto;
import com.auditx.common.util.IdempotencyKeyGenerator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Order(3)
@Component
public class SyslogParsingStrategy implements ParsingStrategy {

    private static final Pattern PRIORITY_PATTERN = Pattern.compile("^<\\d+>(.+)$", Pattern.DOTALL);
    private static final Pattern SUPPORTS_PATTERN = Pattern.compile("^<\\d+>.*");

    // Key=value extraction patterns
    private static final Pattern KV_USER_ID   = Pattern.compile("userId=(\\S+)");
    private static final Pattern KV_ACTION    = Pattern.compile("action=(\\S+)");
    private static final Pattern KV_OUTCOME   = Pattern.compile("outcome=(\\S+)");
    private static final Pattern KV_SOURCE_IP = Pattern.compile("sourceIp=(\\S+)");
    private static final Pattern KV_TENANT_ID = Pattern.compile("tenantId=(\\S+)");

    // RFC 3164 header: "MMM DD HH:MM:SS hostname appname[pid]: message"
    private static final Pattern RFC3164_PATTERN = Pattern.compile(
            "^(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s+(\\d{1,2})\\s+(\\d{2}:\\d{2}:\\d{2})\\s+(\\S+)\\s+(\\S+):\\s*(.*)$",
            Pattern.DOTALL
    );

    // Patterns to extract userId from RFC 3164 message body
    private static final Pattern USER_WORD_PATTERN = Pattern.compile("user\\s+(\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FOR_USER_PATTERN  = Pattern.compile("for\\s+(\\S+)", Pattern.CASE_INSENSITIVE);

    @Override
    public boolean supports(String payload) {
        if (payload == null) return false;
        String trimmed = payload.trim();
        return trimmed.startsWith("<") && SUPPORTS_PATTERN.matcher(trimmed).matches();
    }

    @Override
    public StructuredEventDto parse(String payload) {
        Matcher priorityMatcher = PRIORITY_PATTERN.matcher(payload.trim());
        if (!priorityMatcher.matches()) {
            throw new IllegalArgumentException("Syslog payload does not match priority pattern: " + payload);
        }

        String message = priorityMatcher.group(1).trim();

        // Try key=value extraction first
        Matcher userIdMatcher   = KV_USER_ID.matcher(message);
        Matcher actionMatcher   = KV_ACTION.matcher(message);
        Matcher outcomeMatcher  = KV_OUTCOME.matcher(message);
        Matcher sourceIpMatcher = KV_SOURCE_IP.matcher(message);
        Matcher tenantIdMatcher = KV_TENANT_ID.matcher(message);

        boolean hasKv = userIdMatcher.find() || actionMatcher.find()
                || outcomeMatcher.find() || sourceIpMatcher.find() || tenantIdMatcher.find();

        String userId   = null;
        String action   = null;
        String outcome  = "UNKNOWN";
        String sourceIp = null;
        String tenantId = null;

        if (hasKv) {
            // Re-run finders since find() advances state
            Matcher m;

            m = KV_USER_ID.matcher(message);
            if (m.find()) userId = m.group(1);

            m = KV_ACTION.matcher(message);
            if (m.find()) action = m.group(1);

            m = KV_OUTCOME.matcher(message);
            if (m.find()) outcome = m.group(1);

            m = KV_SOURCE_IP.matcher(message);
            if (m.find()) sourceIp = m.group(1);

            m = KV_TENANT_ID.matcher(message);
            if (m.find()) tenantId = m.group(1);
        } else {
            // Fall back to RFC 3164 header parsing
            Matcher rfcMatcher = RFC3164_PATTERN.matcher(message);
            if (rfcMatcher.matches()) {
                sourceIp = rfcMatcher.group(4); // hostname
                // App name without PID brackets
                action = rfcMatcher.group(5).replaceAll("\\[.*\\]", "");
                String msgBody = rfcMatcher.group(6);

                // Try to extract userId from message body
                Matcher userWordMatcher = USER_WORD_PATTERN.matcher(msgBody);
                Matcher forUserMatcher  = FOR_USER_PATTERN.matcher(msgBody);
                if (userWordMatcher.find()) {
                    userId = userWordMatcher.group(1);
                } else if (forUserMatcher.find()) {
                    userId = forUserMatcher.group(1);
                }
            }
        }

        return new StructuredEventDto(
                IdempotencyKeyGenerator.generate(),
                tenantId,
                userId,
                action,
                sourceIp,
                outcome,
                Instant.now(),
                null,
                null,
                null
        );
    }
}
