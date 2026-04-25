package com.auditx.parser.strategy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ParsingStrategyRegistry {

    private final List<ParsingStrategy> strategies;

    public ParsingStrategyRegistry(List<ParsingStrategy> strategies) {
        this.strategies = strategies;
    }

    public Optional<ParsingStrategy> find(String payload) {
        return strategies.stream()
                .filter(s -> s.supports(payload))
                .findFirst();
    }
}
