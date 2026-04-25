package com.auditx.common.exception;

public class DuplicateEventException extends RuntimeException {
    public DuplicateEventException(String eventId) {
        super("Duplicate event: " + eventId);
    }
}
