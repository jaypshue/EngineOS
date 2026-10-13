package com.engineos.infrastructure.git;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessGitRepositoryTest {
    @TempDir
    Path workspace;

    @Test
    void parsesStatusBranchAndStagedState() {
        var runner = new RecordingRunner();
        runner.add(List.of("status", "--porcelain=v1", "-b"),
                new GitCommandResult(0,
                        "## feature/demo...origin/feature/demo [ahead 1]\n"
                                + " M src/changed.java\n"
                                + "A  src/staged.java\n"
                                + "?? notes.txt\n"
                                + "R  old.java -> new.java\n",
                        ""));

        var status = new ProcessGitRepository(runner).status(workspace);

        assertTrue(status.repository());
        assertEquals("feature/demo", status.branch());
        assertFalse(status.clean());
        assertEquals(4, status.changes().size());
        assertTrue(status.changes().get(0).unstaged());
        assertTrue(status.changes().get(1).staged());
        assertEquals("old.java", status.changes().get(3).originalPath());
        assertEquals("new.java", status.changes().get(3).path());
    }

    @Test
    void mapsHistoryDiffBranchesAndCommitWithoutStaging() {
        var runner = new RecordingRunner();
        runner.add(List.of("log", "-n", "2", "--date=iso-strict",
                        "--pretty=format:%H\u001f%h\u001f%an\u001f%aI\u001f%s\u001e"),
                new GitCommandResult(0,
                        "abcdef\u001fabc\u001fAda\u001f2026-09-21T12:00:00Z\u001ffirst\u001e",
                        ""));
        runner.add(List.of("diff", "--no-ext-diff"), new GitCommandResult(0, "working", ""));
        runner.add(List.of("diff", "--cached", "--no-ext-diff"), new GitCommandResult(0, "staged", ""));
        runner.add(List.of("show", "--no-ext-diff", "--format=fuller", "abc"),
                new GitCommandResult(0, "commit diff", ""));
        runner.add(List.of("branch", "--format=%(HEAD)\u001f%(refname:short)"),
                new GitCommandResult(0, "*\u001fmain\n \u001ffeature\n", ""));
        runner.add(List.of("status", "--porcelain=v1", "-b"),
                new GitCommandResult(0, "## main\nA  staged.txt\n", ""));
        runner.add(List.of("commit", "-m", "message"),
                new GitCommandResult(0, "[main abc123] message", ""));
        runner.add(List.of("rev-parse", "HEAD"),
                new GitCommandResult(0, "abc123\n", ""));

        var git = new ProcessGitRepository(runner);

        assertEquals("first", git.recentCommits(workspace, 2).getFirst().message());
        assertEquals("working", git.workingTreeDiff(workspace).text());
        assertEquals("staged", git.stagedDiff(workspace).text());
        assertEquals("commit diff", git.commitDiff(workspace, "abc").text());
        assertEquals(List.of(new GitBranch("main", true), new GitBranch("feature", false)),
                git.branches(workspace));
        var commit = git.commit(workspace, "message");
        assertTrue(commit.succeeded());
        assertEquals("abc123", commit.commitHash());
        assertTrue(runner.commands.stream().noneMatch(command -> command.contains("add")));
    }

    @Test
    void handlesNonRepositoryAndMissingCommitMessage() {
        var runner = new RecordingRunner();
        runner.add(List.of("status", "--porcelain=v1", "-b"),
                new GitCommandResult(128, "", "not a git repository"));

        var git = new ProcessGitRepository(runner);

        assertFalse(git.status(workspace).repository());
        var commit = git.commit(workspace, " ");
        assertFalse(commit.succeeded());
        assertEquals("Commit message is required.", commit.error());
    }

    @Test
    void reportsNothingStagedWithoutRunningCommit() {
        var runner = new RecordingRunner();
        runner.add(List.of("status", "--porcelain=v1", "-b"),
                new GitCommandResult(0, "## main\n M unstaged.txt\n", ""));

        var commit = new ProcessGitRepository(runner).commit(workspace, "message");

        assertFalse(commit.succeeded());
        assertEquals("Nothing staged to commit.", commit.error());
        assertTrue(runner.commands.stream().noneMatch(command -> command.contains("commit")));
    }

    @Test
    void commitsOnlyStagedFilesAndReturnsHashFromRealRepository() throws Exception {
        runGit("init", "-b", "main");
        runGit("config", "user.name", "EngineOS Test");
        runGit("config", "user.email", "engineos@example.test");
        Files.writeString(workspace.resolve("staged.txt"), "staged");
        Files.writeString(workspace.resolve("unstaged.txt"), "unstaged");
        runGit("add", "staged.txt");

        var result = new ProcessGitRepository().commit(workspace, "controlled commit");

        assertTrue(result.succeeded(), result.error());
        assertTrue(result.commitHash().matches("[0-9a-f]{40}"));
        assertEquals("staged", Files.readString(workspace.resolve("staged.txt")));
        assertTrue(Files.exists(workspace.resolve("unstaged.txt")));
        assertTrue(new ProcessGitRepository().status(workspace).changes().stream()
                .anyMatch(change -> change.path().equals("unstaged.txt") && change.unstaged()));
        assertEquals(result.commitHash(),
                new ProcessGitRepository().recentCommits(workspace, 1).getFirst().hash());
    }

    @Test
    void capturesBaselineAndChangedWorkingTreeState() throws Exception {
        runGit("init", "-b", "main");
        runGit("config", "user.name", "EngineOS Test");
        runGit("config", "user.email", "engineos@example.test");
        Files.writeString(workspace.resolve("tracked.txt"), "baseline\n");
        runGit("add", "tracked.txt");
        runGit("commit", "-m", "baseline");

        var git = new ProcessGitRepository();
        var baseline = git.snapshot(workspace);
        Files.writeString(workspace.resolve("tracked.txt"), "baseline\nchanged\n");
        Files.writeString(workspace.resolve("new.txt"), "new\nfile\n");
        var after = git.snapshot(workspace);

        assertEquals("main", baseline.branch());
        assertTrue(baseline.clean());
        assertTrue(baseline.headSha().matches("[0-9a-f]{40}"));
        assertFalse(after.clean());
        assertEquals(2, after.changes().size());
        assertEquals(3, after.linesAdded());
        assertEquals(0, after.linesRemoved());
        assertEquals(baseline.headSha(), after.headSha());
    }

    @Test
    void resolvesParentRepositoryWhenWorkspaceIsNested() throws Exception {
        runGit("init", "-b", "main");
        runGit("config", "user.name", "EngineOS Test");
        runGit("config", "user.email", "engineos@example.test");
        Files.createDirectories(workspace.resolve("nested").resolve("system"));
        Files.writeString(workspace.resolve("nested").resolve("system").resolve("README.md"), "system\n");
        runGit("add", ".");
        runGit("commit", "-m", "baseline");

        var nested = workspace.resolve("nested").resolve("system");
        var snapshot = new ProcessGitRepository().snapshot(nested);

        assertEquals(workspace.toAbsolutePath().normalize(), snapshot.repositoryRoot());
        assertEquals("main", snapshot.branch());
        assertTrue(snapshot.clean());
    }

    @Test
    void surfacesGitCommitFailure() {
        var runner = new RecordingRunner();
        runner.add(List.of("status", "--porcelain=v1", "-b"),
                new GitCommandResult(0, "## main\nA  staged.txt\n", ""));
        runner.add(List.of("commit", "-m", "message"),
                new GitCommandResult(1, "output", "hook rejected"));

        var result = new ProcessGitRepository(runner).commit(workspace, "message");

        assertFalse(result.succeeded());
        assertEquals("hook rejected", result.error());
    }

    @Test
    void convertsCommandRunnerFailuresIntoRepositoryResults() {
        GitCommandRunner failingRunner = (root, arguments) -> {
            throw new IOException("git executable unavailable");
        };
        var git = new ProcessGitRepository(failingRunner);

        assertFalse(git.status(workspace).repository());
        assertTrue(git.recentCommits(workspace, 5).isEmpty());
        assertTrue(git.workingTreeDiff(workspace).text().contains("git executable unavailable"));
        assertFalse(git.commit(workspace, "message").succeeded());
    }

    private void runGit(String... arguments) throws IOException, InterruptedException {
        var command = new ArrayList<String>();
        command.add("git");
        command.addAll(List.of(arguments));
        var process = new ProcessBuilder(command)
                .directory(workspace.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        assertEquals(0, process.waitFor(), output);
    }

    private static final class RecordingRunner implements GitCommandRunner {
        private final List<List<String>> commands = new ArrayList<>();
        private final List<GitCommandResult> results = new ArrayList<>();

        private void add(List<String> command, GitCommandResult result) {
            commands.add(command);
            results.add(result);
        }

        @Override
        public GitCommandResult run(Path workspace, List<String> arguments) {
            int index = commands.indexOf(arguments);
            return index < 0 ? new GitCommandResult(1, "", "unexpected command") : results.get(index);
        }
    }
}
