package com.auditx.piidetector.service;

import com.auditx.common.dto.PiiFindingDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PiiScannerServiceTest {

    private PiiScannerService service;

    @BeforeEach
    void setUp() {
        service = new PiiScannerService();
    }

    @Test
    void scan_email_detected() {
        List<PiiFindingDto.PiiMatch> matches = service.scan("Contact us at user@example.com for support");
        assertThat(matches).isNotEmpty();
        assertThat(matches).anyMatch(m -> "EMAIL".equals(m.type()));
    }

    @Test
    void scan_aadhaar_detected() {
        List<PiiFindingDto.PiiMatch> matches = service.scan("Aadhaar number: 234567890123");
        assertThat(matches).isNotEmpty();
        assertThat(matches).anyMatch(m -> "AADHAAR".equals(m.type()));
    }

    @Test
    void scan_pan_detected() {
        List<PiiFindingDto.PiiMatch> matches = service.scan("PAN card: ABCDE1234F");
        assertThat(matches).isNotEmpty();
        assertThat(matches).anyMatch(m -> "PAN".equals(m.type()));
    }

    @Test
    void scan_phone_detected() {
        List<PiiFindingDto.PiiMatch> matches = service.scan("Call us on 9876543210");
        assertThat(matches).isNotEmpty();
        assertThat(matches).anyMatch(m -> "PHONE_IN".equals(m.type()));
    }

    @Test
    void scan_noMatch_returnsEmpty() {
        List<PiiFindingDto.PiiMatch> matches =
                service.scan("timestamp=2024 userId=alice action=LOGIN");
        assertThat(matches).isEmpty();
    }

    @Test
    void scan_null_returnsEmpty() {
        List<PiiFindingDto.PiiMatch> matches = service.scan(null);
        assertThat(matches).isEmpty();
    }

    @Test
    void scan_multipleTypes_allDetected() {
        List<PiiFindingDto.PiiMatch> matches =
                service.scan("email=user@example.com pan=ABCDE1234F");
        assertThat(matches).hasSizeGreaterThanOrEqualTo(2);
        assertThat(matches).anyMatch(m -> "EMAIL".equals(m.type()));
        assertThat(matches).anyMatch(m -> "PAN".equals(m.type()));
    }

    @Test
    void mask_shortValue_fourStars() {
        // mask() is package-private — invoke via scan output on a short match
        // and also directly since it's package-private (same package in test)
        String result = service.mask("ab");
        assertThat(result).isEqualTo("****");
    }

    @Test
    void scan_maskedValue_startsWithFirstTwoCharsAndEndsWithLastTwo() {
        List<PiiFindingDto.PiiMatch> matches = service.scan("email: user@example.com");
        PiiFindingDto.PiiMatch emailMatch = matches.stream()
                .filter(m -> "EMAIL".equals(m.type()))
                .findFirst()
                .orElseThrow();

        String masked = emailMatch.maskedValue();
        String original = "user@example.com";
        assertThat(masked).startsWith(original.substring(0, 2));
        assertThat(masked).endsWith(original.substring(original.length() - 2));
        assertThat(masked).contains("*");
    }
}
