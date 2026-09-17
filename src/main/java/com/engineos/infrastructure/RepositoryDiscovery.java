package com.engineos.infrastructure;

import com.engineos.domain.Evidence;
import com.engineos.domain.WorkspaceContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public final class RepositoryDiscovery {
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
            var gitBranch = runGit(root, "branch", "--show-current").trim();
            var status = runGit(root, "status", "--porcelain").trim();
            return new WorkspaceContext(root.toAbsolutePath().normalize(), root.getFileName().toString(),
                    gitBranch, status.isEmpty(), files);
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

    private static String runGit(Path root, String... arguments) throws IOException {
        try {
            var command = new java.util.ArrayList<String>();
            command.add("git");
            command.addAll(List.of(arguments));
            var process = new ProcessBuilder(command).directory(root.toFile()).start();
            var result = new String(process.getInputStream().readAllBytes());
            process.waitFor();
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Git discovery interrupted", exception);
        }
    }
}
