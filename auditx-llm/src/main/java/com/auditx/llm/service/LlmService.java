package com.auditx.llm.service;

import com.auditx.llm.provider.LlmProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);
    private static final String FALLBACK_MESSAGE = "LLM service temporarily unavailable";

    private final LlmProvider llmProvider;
    private final PromptTemplateService promptTemplateService;

    public LlmService(LlmProvider llmProvider, PromptTemplateService promptTemplateService) {
        this.llmProvider = llmProvider;
        this.promptTemplateService = promptTemplateService;
    }

    public Mono<String> complete(String templateName, Map<String, String> variables,
                                  String tenantId, String feature) {
        String prompt = promptTemplateService.render(templateName, variables);
        AtomicInteger attemptCount = new AtomicInteger(0);

        return llmProvider.complete(prompt)
                .doOnSubscribe(s -> attemptCount.incrementAndGet())
                .retryWhen(Retry.backoff(3, Duration.ofMillis(500))
                        .doBeforeRetry(signal -> attemptCount.incrementAndGet())
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .onErrorResume(ex -> {
                    log.error("{\"tenantId\":\"{}\",\"feature\":\"{}\",\"attemptCount\":{},\"errorMessage\":\"{}\"}",
                            tenantId, feature, attemptCount.get(), ex.getMessage());
                    return Mono.just(FALLBACK_MESSAGE);
                });
    }
}
