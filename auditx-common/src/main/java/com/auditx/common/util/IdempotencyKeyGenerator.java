package com.auditx.common.util;

import java.util.UUID;

public final class IdempotencyKeyGenerator {
    public static String generate() {
        return UUID.randomUUID().toString();
    }

    private IdempotencyKeyGenerator() {}
}
