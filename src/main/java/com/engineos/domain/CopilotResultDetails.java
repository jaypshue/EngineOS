package com.engineos.domain;

import java.util.List;

public record CopilotResultDetails(
        boolean eventPresent,
        String actualModel,
        Long totalApiDurationMs,
        Long sessionDurationMs,
        Integer linesAdded,
        Integer linesRemoved,
        Integer filesModified,
        List<String> modifiedFiles) {

    public CopilotResultDetails {
        modifiedFiles = modifiedFiles == null ? List.of() : List.copyOf(modifiedFiles);
    }

    public static CopilotResultDetails unavailable() {
        return new CopilotResultDetails(false, null, null, null, null, null, null, List.of());
    }

    public boolean available() {
        return eventPresent;
    }

    public boolean complete() {
        return eventPresent
                && actualModel != null
                && totalApiDurationMs != null
                && linesAdded != null
                && linesRemoved != null
                && filesModified != null;
    }
}
