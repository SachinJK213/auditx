package com.auditx.parser.strategy;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ParsingStrategyRegistryTest {

    @Test
    void find_returnsFirstMatchingStrategy() {
        ParsingStrategy s1 = mock(ParsingStrategy.class);
        ParsingStrategy s2 = mock(ParsingStrategy.class);
        when(s1.supports("payload")).thenReturn(false);
        when(s2.supports("payload")).thenReturn(true);

        ParsingStrategyRegistry registry = new ParsingStrategyRegistry(List.of(s1, s2));
        Optional<ParsingStrategy> result = registry.find("payload");

        assertThat(result).isPresent().contains(s2);
        verify(s1).supports("payload");
        verify(s2).supports("payload");
    }

    @Test
    void find_noMatchingStrategy_returnsEmpty() {
        ParsingStrategy s1 = mock(ParsingStrategy.class);
        when(s1.supports(any())).thenReturn(false);

        ParsingStrategyRegistry registry = new ParsingStrategyRegistry(List.of(s1));
        assertThat(registry.find("unrecognised")).isEmpty();
    }

    @Test
    void find_emptyRegistry_returnsEmpty() {
        ParsingStrategyRegistry registry = new ParsingStrategyRegistry(List.of());
        assertThat(registry.find("anything")).isEmpty();
    }

    @Test
    void find_multipleMatches_returnsFirst() {
        ParsingStrategy s1 = mock(ParsingStrategy.class);
        ParsingStrategy s2 = mock(ParsingStrategy.class);
        when(s1.supports(any())).thenReturn(true);
        when(s2.supports(any())).thenReturn(true);

        ParsingStrategyRegistry registry = new ParsingStrategyRegistry(List.of(s1, s2));
        assertThat(registry.find("p")).isPresent().contains(s1);
        // s2 should never be checked once s1 matches
        verify(s2, never()).supports(any());
    }
}
