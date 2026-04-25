package com.auditx.common.dto;

import java.util.List;

public record BatchIngestionResult(
        int total,
        int accepted,
        int failed,
        List<String> eventIds
) {}
