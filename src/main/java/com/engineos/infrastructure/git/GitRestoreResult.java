package com.engineos.infrastructure.git;

import java.util.List;

public record GitRestoreResult(
        List<String> restoredFiles,
        List<String> preservedFiles,
        List<String> errors) {
    public GitRestoreResult {
        restoredFiles = restoredFiles == null ? List.of() : List.copyOf(restoredFiles);
        preservedFiles = preservedFiles == null ? List.of() : List.copyOf(preservedFiles);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public boolean succeeded() {
        return errors.isEmpty();
    }

    public static GitRestoreResult unavailable() {
        return new GitRestoreResult(List.of(), List.of(), List.of("Workspace restore is unavailable."));
    }
}
