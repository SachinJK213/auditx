package com.auditx.llm.provider;

import reactor.core.publisher.Mono;

public interface LlmProvider {
    Mono<String> complete(String prompt);
}
