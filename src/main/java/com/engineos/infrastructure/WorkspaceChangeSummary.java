package com.engineos.infrastructure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Final workspace change accounting based on the repository state at capture time.
 */
public record WorkspaceChangeSummary(
        int linesAdded,
        int linesRemoved,
        int filesModified,
        List<String> modifiedFiles) {

    public WorkspaceChangeSummary {
        modifiedFiles = modifiedFiles == null ? List.of() : List.copyOf(modifiedFiles);
    }

    public static WorkspaceChangeSummary capture(Path root) {
        Objects.requireNonNull(root, "Workspace root is required");
        if (!Files.isDirectory(root.resolve(".git"))) {
            return null;
        }
        try {
            String status = runGit(root, "status", "--short", "--untracked-files=all");
            String numstat = runGit(root, "diff", "--numstat", "HEAD", "--");
            var files = new ArrayList<String>();
            int added = 0;
            int removed = 0;

            for (String line : numstat.lines().toList()) {
                if (line.isBlank()) {
                    continue;
                }
                String[] columns = line.split("\\t", 3);
                if (columns.length != 3 || !isRelevant(columns[2])) {
                    continue;
                }
                added += parseCount(columns[0]);
                removed += parseCount(columns[1]);
                files.add(columns[2]);
            }

            for (String line : status.lines().toList()) {
                if (!line.startsWith("?? ")) {
                    continue;
                }
                String path = line.substring(3).trim();
                if (!isRelevant(path) || files.contains(path)) {
                    continue;
                }
                files.add(path);
                Path file = root.resolve(path);
                if (Files.isRegularFile(file)) {
                    added += countLines(file);
                }
            }

            files.sort(String::compareTo);
            return new WorkspaceChangeSummary(added, removed, files.size(), files);
        } catch (IOException exception) {
            return null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static String runGit(Path root, String... arguments)
            throws IOException, InterruptedException {
        var command = new ArrayList<String>();
        command.add("git");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command)
                    .directory(root.toFile())
                    .redirectErrorStream(true)
                    .start();
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("git command timed out");
            }
            return new String(process.getInputStream().readAllBytes());
        } finally {
            process.getInputStream().close();
            process.getErrorStream().close();
            process.getOutputStream().close();
            process.destroy();
        }
    }

    private static int parseCount(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static int countLines(Path file) throws IOException {
        try (var lines = Files.lines(file)) {
            return (int) lines.count();
        }
    }

    private static boolean isRelevant(String path) {
        String normalized = path.replace('\\', '/');
        return normalized.startsWith("src/")
                || normalized.equals("pom.xml")
                || normalized.startsWith("scripts/")
                || normalized.startsWith("README");
    }
}
