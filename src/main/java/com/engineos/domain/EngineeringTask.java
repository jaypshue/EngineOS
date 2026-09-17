package com.engineos.domain;

import java.time.Instant;
import java.util.UUID;

public final class EngineeringTask {
    public enum Status {
        PROPOSED, APPROVED, COMPLETED, REJECTED
    }

    private final UUID id = UUID.randomUUID();
    private final String title;
    private final String intent;
    private final String rationale;
    private final Instant createdAt = Instant.now();
    private Status status = Status.PROPOSED;

    public EngineeringTask(String title, String intent, String rationale) {
        this.title = requireText(title, "Task title");
        this.intent = requireText(intent, "Task intent");
        this.rationale = rationale == null ? "" : rationale.trim();
    }

    public UUID id() { return id; }
    public String title() { return title; }
    public String intent() { return intent; }
    public String rationale() { return rationale; }
    public Instant createdAt() { return createdAt; }
    public Status status() { return status; }

    public void approve() {
        requireStatus(Status.PROPOSED);
        status = Status.APPROVED;
    }

    public void reject() {
        requireStatus(Status.PROPOSED);
        status = Status.REJECTED;
    }

    public void complete() {
        requireStatus(Status.APPROVED);
        status = Status.COMPLETED;
    }

    private void requireStatus(Status expected) {
        if (status != expected) {
            throw new IllegalStateException("Task must be " + expected + " but was " + status);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
