package com.engineos.domain;

import java.nio.file.Path;
import java.util.List;

public record WorkspaceContext(
        Path root,
        String repositoryName,
        String gitBranch,
        boolean gitWorktreeClean,
        List<Path> discoveredFiles) {

    public WorkspaceContext {
        if (root == null) {
            throw new IllegalArgumentException("Workspace root is required");
        }
        repositoryName = repositoryName == null ? "" : repositoryName;
        gitBranch = gitBranch == null ? "" : gitBranch;
        discoveredFiles = List.copyOf(discoveredFiles == null ? List.of() : discoveredFiles);
    }
}
