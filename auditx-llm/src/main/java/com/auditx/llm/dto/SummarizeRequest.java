package com.auditx.llm.dto;

import java.util.List;

public record SummarizeRequest(List<String> alertIds, String tenantId) {}
