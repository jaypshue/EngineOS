package com.engineos.infrastructure.git;

import java.nio.file.Path;
import java.util.List;

public record GitSnapshot(
        Path repositoryRoot,
        String branch,
        String headSha,
        boolean clean,
        List<GitFileChange> changes,
        int linesAdded,
        int linesRemoved) {
    public GitSnapshot {
        changes = changes == null ? List.of() : List.copyOf(changes);
        branch = branch == null ? "" : branch;
        headSha = headSha == null ? "" : headSha;
    }

    public boolean repository() {
        return repositoryRoot != null && (!branch.isBlank() || !headSha.isBlank());
    }

    public static GitSnapshot unavailable(Path workspace) {
        return new GitSnapshot(workspace, "", "", true, List.of(), 0, 0);
    }
}
