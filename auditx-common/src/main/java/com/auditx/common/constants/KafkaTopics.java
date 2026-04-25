package com.auditx.common.constants;

public final class KafkaTopics {
    public static final String RAW_EVENTS        = "raw-events";
    public static final String STRUCTURED_EVENTS = "structured-events";
    public static final String ALERTS            = "alerts";
    public static final String POLICY_VIOLATIONS = "policy-violations";
    public static final String PII_FINDINGS      = "pii-findings";
    public static final String ENRICHED_EVENTS   = "enriched-events";
    public static final String NOTIFICATIONS     = "notifications";

    private KafkaTopics() {}
}
