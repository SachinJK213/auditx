package com.auditx.common.util;

import org.slf4j.MDC;

public final class MdcUtil {
    public static final String TRACE_ID = "traceId";
    public static final String TENANT_ID = "tenantId";
    public static final String USER_ID = "userId";
    public static final String EVENT_ID = "eventId";
    public static final String SERVICE = "service";

    public static void setRequestContext(String traceId, String tenantId, String userId) {
        MDC.put(TRACE_ID, traceId != null ? traceId : "unknown");
        MDC.put(TENANT_ID, tenantId != null ? tenantId : "unknown");
        MDC.put(USER_ID, userId != null ? userId : "unknown");
    }

    public static void setEventContext(String traceId, String tenantId, String eventId) {
        MDC.put(TRACE_ID, traceId != null ? traceId : "unknown");
        MDC.put(TENANT_ID, tenantId != null ? tenantId : "unknown");
        MDC.put(EVENT_ID, eventId != null ? eventId : "unknown");
    }

    public static void clear() {
        MDC.clear();
    }

    private MdcUtil() {}
}
