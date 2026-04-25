package com.auditx.llm.config;

import com.auditx.common.util.MdcUtil;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .filter((request, next) -> {
                    String traceId = MDC.get(MdcUtil.TRACE_ID);
                    if (traceId != null) {
                        return next.exchange(ClientRequest.from(request)
                                .header("X-Trace-Id", traceId)
                                .build());
                    }
                    return next.exchange(request);
                })
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }
}
