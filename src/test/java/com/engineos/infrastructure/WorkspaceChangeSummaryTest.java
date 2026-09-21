package com.engineos.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceChangeSummaryTest {
    @TempDir
    Path project;

    @Test
    void countsFinalTrackedAndUntrackedWorkspaceState() throws Exception {
        runGit("init");
        runGit("config", "user.email", "engineos-test@example.invalid");
        runGit("config", "user.name", "EngineOS Test");
        Path tracked = project.resolve("src/main.java");
        Files.createDirectories(tracked.getParent());
        Files.writeString(tracked, "one\n");
        runGit("add", ".");
        runGit("commit", "-m", "baseline");

        Files.writeString(tracked, "one\ntwo\nthree\n");
        Path untracked = project.resolve("src/new.java");
        Files.writeString(untracked, "new\nfile\n");

        var summary = WorkspaceChangeSummary.capture(project);

        assertEquals(4, summary.linesAdded());
        assertEquals(0, summary.linesRemoved());
        assertEquals(2, summary.filesModified());
        assertTrue(summary.modifiedFiles().contains("src/main.java"));
        assertTrue(summary.modifiedFiles().contains("src/new.java"));
    }

    private void runGit(String... arguments) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add("git");
        command.addAll(java.util.List.of(arguments));
        Process process = new ProcessBuilder(command)
                .directory(project.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new AssertionError("git failed: " + output);
        }
    }
}
