package com.engineos.infrastructure;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public record WorkspaceSnapshot(List<String> relevantChanges) {
    public WorkspaceSnapshot {
        relevantChanges = relevantChanges == null ? List.of() : List.copyOf(relevantChanges);
    }

    public static WorkspaceSnapshot capture(Path root) {
        Objects.requireNonNull(root, "Workspace root is required");
        try {
            Process process = new ProcessBuilder("git", "status", "--short", "--untracked-files=all")
                    .directory(root.toFile())
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return captureFilesystem(root);
            }
            String output = new String(process.getInputStream().readAllBytes());
            if (process.exitValue() != 0) {
                return captureFilesystem(root);
            }
            return new WorkspaceSnapshot(output.lines()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .filter(WorkspaceSnapshot::isRelevant)
                    .map(line -> line + " | " + contentHash(root, pathOf(line)))
                    .sorted()
                    .toList());
        } catch (IOException exception) {
            return new WorkspaceSnapshot(List.of("workspace snapshot unavailable: " + exception.getMessage()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new WorkspaceSnapshot(List.of("workspace snapshot interrupted"));
        }
    }

    private static WorkspaceSnapshot captureFilesystem(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            return new WorkspaceSnapshot(paths
                    .filter(Files::isRegularFile)
                    .map(root::relativize)
                    .map(Path::toString)
                    .map(path -> path.replace('\\', '/'))
                    .filter(WorkspaceSnapshot::isRelevantPath)
                    .map(path -> "?? " + path + " | " + contentHash(root, path))
                    .sorted()
                    .toList());
        } catch (IOException exception) {
            return new WorkspaceSnapshot(List.of("workspace snapshot unavailable: " + exception.getMessage()));
        }
    }

    public List<String> changedSince(WorkspaceSnapshot baseline) {
        if (baseline == null) {
            return relevantChanges;
        }
        return Stream.concat(relevantChanges.stream(), baseline.relevantChanges().stream())
                .distinct()
                .filter(change -> !baseline.relevantChanges().contains(change)
                        || !relevantChanges.contains(change))
                .sorted()
                .toList();
    }

    private static boolean isRelevant(String statusLine) {
        return isRelevantPath(pathOf(statusLine));
    }

    private static boolean isRelevantPath(String path) {
        String normalized = path.replace('\\', '/');
        return normalized.startsWith("src/")
                || normalized.equals("pom.xml")
                || normalized.startsWith("scripts/")
                || normalized.startsWith("README");
    }

    private static String pathOf(String statusLine) {
        return statusLine.length() > 3 ? statusLine.substring(3).trim() : statusLine;
    }

    private static String contentHash(Path root, String relativePath) {
        try {
            Path path = root.resolve(relativePath);
            if (!Files.isRegularFile(path)) {
                return "missing";
            }
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
            StringBuilder result = new StringBuilder();
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (IOException | NoSuchAlgorithmException exception) {
            return "unavailable";
        }
    }
}
