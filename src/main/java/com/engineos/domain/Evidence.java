package com.engineos.domain;

import java.time.Instant;
import java.util.UUID;

public record Evidence(
        UUID id,
        EvidenceType type,
        String summary,
        String detail,
        Instant recordedAt) {

    public Evidence {
        id = id == null ? UUID.randomUUID() : id;
        type = type == null ? EvidenceType.EXECUTION_RESULT : type;
        summary = requireText(summary, "Evidence summary");
        detail = detail == null ? "" : detail;
        recordedAt = recordedAt == null ? Instant.now() : recordedAt;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    public enum EvidenceType {
        REPOSITORY_DISCOVERY,
        EXECUTION_RESULT,
        ENGINEERING_REVIEW
    }
}
