package com.auditx.sdk.interceptor;

import com.auditx.common.dto.RawEventDto;
import com.auditx.common.enums.PayloadType;
import com.auditx.sdk.AuditxProperties;
import com.auditx.sdk.sender.AuditxEventSender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class AuditxHandlerInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuditxHandlerInterceptor.class);
    private static final String START_TIME_ATTR = "auditx.startTime";

    private final AuditxEventSender sender;
    private final AuditxProperties properties;
    private final ObjectMapper objectMapper;

    public AuditxHandlerInterceptor(AuditxEventSender sender,
                                     AuditxProperties properties,
                                     ObjectMapper objectMapper) {
        this.sender = sender;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        try {
            String method = request.getMethod();
            String path = request.getRequestURI();
            int statusCode = response.getStatus();

            String userId = resolveUserId();
            String tenantId = properties.getTenantId();

            Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
            long durationMs = startTime != null ? System.currentTimeMillis() - startTime : 0L;

            String payload = objectMapper.writeValueAsString(Map.of(
                    "method", method,
                    "path", path,
                    "statusCode", statusCode,
                    "userId", userId,
                    "tenantId", tenantId != null ? tenantId : "unknown",
                    "durationMs", durationMs
            ));

            RawEventDto event = new RawEventDto(
                    UUID.randomUUID().toString(),
                    tenantId,
                    payload,
                    PayloadType.STRUCTURED,
                    null,
                    Instant.now()
            );

            sender.send(event);
        } catch (JsonProcessingException e) {
            log.warn("AuditX SDK: failed to serialize interceptor event payload", e);
        }
    }

    private String resolveUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getName() != null) {
                return auth.getName();
            }
        } catch (Exception ignored) {
            // SecurityContextHolder not available in this context
        }
        return "anonymous";
    }
}
