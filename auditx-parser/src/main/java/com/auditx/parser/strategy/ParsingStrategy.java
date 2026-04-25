package com.auditx.parser.strategy;

import com.auditx.common.dto.StructuredEventDto;

public interface ParsingStrategy {
    boolean supports(String payload);
    StructuredEventDto parse(String payload);
}
