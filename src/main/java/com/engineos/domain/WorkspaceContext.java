package com.engineos.domain;

import java.nio.file.Path;
import java.util.List;

public record WorkspaceContext(
        Path root,
        String repositoryName,
        String gitBranch,
        boolean gitWorktreeClean,
        List<Path> discoveredFiles,
        Path rulesPath,
        String rulesContent,
        String rulesSha256) {

    public WorkspaceContext(Path root, String repositoryName, String gitBranch,
                            boolean gitWorktreeClean, List<Path> discoveredFiles) {
        this(root, repositoryName, gitBranch, gitWorktreeClean, discoveredFiles, null, "", "");
    }

    public WorkspaceContext {
        if (root == null) {
            throw new IllegalArgumentException("Workspace root is required");
        }
        repositoryName = repositoryName == null ? "" : repositoryName;
        gitBranch = gitBranch == null ? "" : gitBranch;
        discoveredFiles = List.copyOf(discoveredFiles == null ? List.of() : discoveredFiles);
        rulesContent = rulesContent == null ? "" : rulesContent;
        rulesSha256 = rulesSha256 == null ? "" : rulesSha256;
    }

    public ProjectRules rules() {
        return new ProjectRules(rulesPath, rulesContent, rulesSha256);
    }
}
