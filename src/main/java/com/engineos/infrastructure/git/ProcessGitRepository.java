package com.engineos.infrastructure.git;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public final class ProcessGitRepository implements GitRepository {
    private static final String FIELD_SEPARATOR = "\u001f";
    private static final String RECORD_SEPARATOR = "\u001e";

    private final GitCommandRunner commands;

    public ProcessGitRepository() {
        this(new ProcessGitCommandRunner());
    }

    public ProcessGitRepository(GitCommandRunner commands) {
        this.commands = commands;
    }

    @Override
    public GitStatus status(Path workspace) {
        try {
            var result = commands.run(workspace, List.of("status", "--porcelain=v1", "-b"));
            if (!result.succeeded()) {
                return GitStatus.notRepository(workspace, diagnostic(result));
            }
            return parseStatus(workspace, result.standardOutput());
        } catch (IOException | InterruptedException | RuntimeException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return GitStatus.notRepository(workspace, exception.getMessage());
        }
    }

    @Override
    public GitSnapshot snapshot(Path workspace) {
        var rootResult = run(workspace, List.of("rev-parse", "--show-toplevel"));
        if (!rootResult.succeeded()) {
            return GitSnapshot.unavailable(workspace);
        }
        Path root = Path.of(rootResult.standardOutput().trim()).toAbsolutePath().normalize();
        GitStatus status = status(root);
        if (!status.repository()) {
            return GitSnapshot.unavailable(workspace);
        }
        var head = run(root, List.of("rev-parse", "HEAD"));
        var stats = run(root, List.of("diff", "--numstat", "HEAD", "--"));
        int added = 0;
        int removed = 0;
        if (stats.succeeded()) {
            for (String line : stats.standardOutput().lines().toList()) {
                String[] columns = line.split("\\t", 3);
                if (columns.length < 2) {
                    continue;
                }
                added += count(columns[0]);
                removed += count(columns[1]);
            }
        }
        for (GitFileChange change : status.changes()) {
            if ("?".equals(change.indexStatus()) && "?".equals(change.workTreeStatus())) {
                try {
                    var file = root.resolve(change.path());
                    if (java.nio.file.Files.isRegularFile(file)) {
                        try (var lines = java.nio.file.Files.lines(file)) {
                            added += (int) lines.count();
                        }
                    }
                } catch (IOException exception) {
                    // Preserve the repository snapshot even when an untracked file cannot be read.
                }
            }
        }
        return new GitSnapshot(
                root,
                status.branch(),
                head.succeeded() ? head.standardOutput().trim() : "",
                status.clean(),
                status.changes(),
                added,
                removed);
    }

    @Override
    public List<GitCommit> recentCommits(Path workspace, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        String format = "%H" + FIELD_SEPARATOR + "%h" + FIELD_SEPARATOR + "%an"
                + FIELD_SEPARATOR + "%aI" + FIELD_SEPARATOR + "%s" + RECORD_SEPARATOR;
        var result = run(workspace, List.of("log", "-n", Integer.toString(limit),
                "--date=iso-strict", "--pretty=format:" + format));
        if (!result.succeeded()) {
            return List.of();
        }
        var commits = new ArrayList<GitCommit>();
        for (String record : result.standardOutput().split(RECORD_SEPARATOR)) {
            String[] fields = record.split(FIELD_SEPARATOR, -1);
            if (fields.length != 5 || fields[0].isBlank()) {
                continue;
            }
            try {
                commits.add(new GitCommit(fields[0], fields[1], fields[4], fields[2],
                        Instant.parse(fields[3])));
            } catch (DateTimeParseException ignored) {
                // Ignore malformed records while preserving usable history.
            }
        }
        return List.copyOf(commits);
    }

    @Override
    public GitDiff workingTreeDiff(Path workspace) {
        return diff(workspace, "working-tree", List.of("diff", "--no-ext-diff"));
    }

    @Override
    public GitDiff stagedDiff(Path workspace) {
        return diff(workspace, "staged", List.of("diff", "--cached", "--no-ext-diff"));
    }

    @Override
    public GitDiff commitDiff(Path workspace, String commit) {
        if (commit == null || commit.isBlank()) {
            return new GitDiff("", "");
        }
        return diff(workspace, commit, List.of("show", "--no-ext-diff", "--format=fuller", commit));
    }

    @Override
    public List<GitBranch> branches(Path workspace) {
        var result = run(workspace,
                List.of("branch", "--format=%(HEAD)" + FIELD_SEPARATOR + "%(refname:short)"));
        if (!result.succeeded()) {
            return List.of();
        }
        return result.standardOutput().lines()
                .filter(line -> !line.isBlank())
                .map(line -> {
                    String[] fields = line.split(FIELD_SEPARATOR, -1);
                    return new GitBranch(fields.length > 1 ? fields[1] : line,
                            fields.length > 0 && "*".equals(fields[0]));
                })
                .toList();
    }

    @Override
    public GitCommitResult commit(Path workspace, String message) {
        if (message == null || message.isBlank()) {
            return new GitCommitResult(false, "", "", "Commit message is required.");
        }
        GitStatus status = status(workspace);
        if (!status.repository()) {
            return new GitCommitResult(false, "", "", status.error().isBlank()
                    ? "Workspace is not a Git repository." : status.error());
        }
        if (status.changes().stream().noneMatch(GitFileChange::staged)) {
            return new GitCommitResult(false, "", "", "Nothing staged to commit.");
        }
        var result = run(workspace, List.of("commit", "-m", message));
        if (!result.succeeded()) {
            return new GitCommitResult(false, "", result.standardOutput(), diagnostic(result));
        }
        var hash = run(workspace, List.of("rev-parse", "HEAD"));
        return new GitCommitResult(true, hash.succeeded() ? hash.standardOutput().trim() : "",
                result.standardOutput(), hash.succeeded() ? "" : diagnostic(hash));
    }

    private GitDiff diff(Path workspace, String reference, List<String> arguments) {
        var result = run(workspace, arguments);
        return new GitDiff(reference, result.succeeded() ? result.standardOutput() : diagnostic(result));
    }

    private GitCommandResult run(Path workspace, List<String> arguments) {
        try {
            return commands.run(workspace, arguments);
        } catch (IOException | InterruptedException | RuntimeException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new GitCommandResult(1, "", exception.getMessage());
        }
    }

    private static GitStatus parseStatus(Path workspace, String output) {
        String branch = "";
        var changes = new ArrayList<GitFileChange>();
        for (String line : output.split("\\R")) {
            if (line.startsWith("## ")) {
                branch = line.substring(3).split("\\.\\.\\.", 2)[0];
                if (branch.equals("HEAD") || branch.contains("(no branch)")) {
                    branch = "(detached HEAD)";
                } else if (branch.startsWith("No commits yet on ")) {
                    branch = branch.substring("No commits yet on ".length());
                }
                continue;
            }
            if (line.length() < 3) {
                continue;
            }
            String originalPath = "";
            String path = line.substring(3);
            if ((line.charAt(0) == 'R' || line.charAt(1) == 'R') && path.contains(" -> ")) {
                String[] renamed = path.split(" -> ", 2);
                originalPath = renamed[0];
                path = renamed[1];
            }
            changes.add(new GitFileChange(path, originalPath,
                    String.valueOf(line.charAt(0)), String.valueOf(line.charAt(1))));
        }
        return new GitStatus(workspace, true, branch, changes.isEmpty(), changes, "");
    }

    private static String diagnostic(GitCommandResult result) {
        String error = result.standardError() == null ? "" : result.standardError().trim();
        return error.isBlank() ? result.standardOutput().trim() : error;
    }

    private static int count(String value) {
        try {
            return "-".equals(value) ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
