package com.auditx.piidetector.service;

import com.auditx.common.dto.PiiFindingDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PiiScannerService {

    // PII regex patterns
    private static final Pattern EMAIL    = Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_IN = Pattern.compile("(\\+91|91)?[6-9]\\d{9}");
    private static final Pattern AADHAAR  = Pattern.compile("\\b[2-9]\\d{11}\\b");
    private static final Pattern PAN      = Pattern.compile("\\b[A-Z]{5}[0-9]{4}[A-Z]\\b");
    private static final Pattern CARD     = Pattern.compile("\\b(?:\\d{4}[\\s\\-]?){3}\\d{4}\\b");
    private static final Pattern SSN      = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");

    private static final Map<String, Pattern> PATTERNS = Map.of(
        "EMAIL",       EMAIL,
        "PHONE_IN",    PHONE_IN,
        "AADHAAR",     AADHAAR,
        "PAN",         PAN,
        "CREDIT_CARD", CARD,
        "SSN",         SSN
    );

    /**
     * Returns list of PiiMatch found in text. Masks matched value (shows first 2 + stars + last 2).
     */
    public List<PiiFindingDto.PiiMatch> scan(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<PiiFindingDto.PiiMatch> matches = new ArrayList<>();
        for (Map.Entry<String, Pattern> entry : PATTERNS.entrySet()) {
            Matcher m = entry.getValue().matcher(text);
            while (m.find()) {
                String raw = m.group();
                String masked = mask(raw);
                matches.add(new PiiFindingDto.PiiMatch(entry.getKey(), masked));
            }
        }
        return matches;
    }

    String mask(String value) {
        if (value.length() <= 4) return "****";
        return value.substring(0, 2) + "*".repeat(value.length() - 4) + value.substring(value.length() - 2);
    }
}
