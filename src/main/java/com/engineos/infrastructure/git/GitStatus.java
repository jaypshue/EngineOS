package com.engineos.infrastructure.git;

import java.nio.file.Path;
import java.util.List;

public record GitStatus(
        Path workspace,
        boolean repository,
        String branch,
        boolean clean,
        List<GitFileChange> changes,
        String error) {
    public GitStatus {
        changes = changes == null ? List.of() : List.copyOf(changes);
        branch = branch == null ? "" : branch;
        error = error == null ? "" : error;
    }

    public static GitStatus notRepository(Path workspace, String error) {
        return new GitStatus(workspace, false, "", true, List.of(), error);
    }
}
