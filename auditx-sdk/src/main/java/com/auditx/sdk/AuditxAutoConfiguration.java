package com.auditx.sdk;

import com.auditx.sdk.interceptor.AuditxHandlerInterceptor;
import com.auditx.sdk.publisher.AuditxLoginEventPublisher;
import com.auditx.sdk.sender.AuditxEventSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@ConditionalOnProperty(prefix = "auditx", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AuditxProperties.class)
public class AuditxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper auditxObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    @ConditionalOnMissingBean
    public RestTemplate auditxRestTemplate() {
        return new RestTemplate();
    }

    @Bean
    public AuditxEventSender auditxEventSender(AuditxProperties properties,
                                                RestTemplate restTemplate,
                                                ObjectMapper objectMapper) {
        return new AuditxEventSender(properties, restTemplate, objectMapper);
    }

    @Bean
    public AuditxHandlerInterceptor auditxHandlerInterceptor(AuditxEventSender sender,
                                                              AuditxProperties properties,
                                                              ObjectMapper objectMapper) {
        return new AuditxHandlerInterceptor(sender, properties, objectMapper);
    }

    @Bean
    public AuditxLoginEventPublisher auditxLoginEventPublisher(AuditxEventSender sender,
                                                                AuditxProperties properties,
                                                                ObjectMapper objectMapper) {
        return new AuditxLoginEventPublisher(sender, properties, objectMapper);
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnClass(name = "org.springframework.web.servlet.config.annotation.WebMvcConfigurer")
    public WebMvcConfigurer auditxWebMvcConfigurer(AuditxHandlerInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor);
            }
        };
    }
}
