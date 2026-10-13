package com.engineos.infrastructure;

import com.engineos.domain.Evidence;
import com.engineos.domain.WorkspaceContext;
import com.engineos.infrastructure.git.GitRepository;
import com.engineos.infrastructure.git.ProcessGitRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;

public final class RepositoryDiscovery {
    private final GitRepository git;

    public RepositoryDiscovery() {
        this(new ProcessGitRepository());
    }

    public RepositoryDiscovery(GitRepository git) {
        this.git = git;
    }

    public WorkspaceContext load(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            throw new IllegalArgumentException("Workspace directory does not exist: " + root);
        }
        try {
            var files = Files.walk(root)
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.toString().contains("target"))
                    .sorted(Comparator.naturalOrder())
                    .toList();
            var gitStatus = git.status(root);
            return new WorkspaceContext(root.toAbsolutePath().normalize(), root.getFileName().toString(),
                    gitStatus.branch(), gitStatus.clean(), files);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to discover workspace: " + exception.getMessage(), exception);
        }
    }

    public Evidence evidence(WorkspaceContext workspace) {
        return new Evidence(null, Evidence.EvidenceType.REPOSITORY_DISCOVERY,
                "Repository discovered: " + workspace.repositoryName(),
                "Branch=" + workspace.gitBranch() + ", clean=" + workspace.gitWorktreeClean()
                        + ", files=" + workspace.discoveredFiles().size(),
                Instant.now());
    }

}
