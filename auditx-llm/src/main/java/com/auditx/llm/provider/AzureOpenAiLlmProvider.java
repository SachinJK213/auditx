package com.auditx.llm.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "auditx.llm.provider", havingValue = "azure-openai")
public class AzureOpenAiLlmProvider implements LlmProvider {

    private final WebClient webClient;
    private final String endpoint;
    private final String apiKey;
    private final String deployment;

    public AzureOpenAiLlmProvider(WebClient webClient,
                                   @Value("${auditx.llm.azure-endpoint}") String endpoint,
                                   @Value("${auditx.llm.api-key}") String apiKey,
                                   @Value("${auditx.llm.azure-deployment:gpt-4o-mini}") String deployment) {
        this.webClient = webClient;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.deployment = deployment;
    }

    @Override
    public Mono<String> complete(String prompt) {
        String uri = endpoint + "/openai/deployments/" + deployment
                + "/chat/completions?api-version=2024-02-01";

        Map<String, Object> body = Map.of(
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        return webClient.post()
                .uri(uri)
                .header("api-key", apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::extractContent);
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<?, ?> response) {
        List<?> choices = (List<?>) response.get("choices");
        Map<?, ?> choice = (Map<?, ?>) choices.get(0);
        Map<?, ?> message = (Map<?, ?>) choice.get("message");
        return (String) message.get("content");
    }
}
