package com.engineos.infrastructure.git;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface GitRepository {
    GitStatus status(Path workspace);

    GitSnapshot snapshot(Path workspace);

    List<GitCommit> recentCommits(Path workspace, int limit);

    GitDiff workingTreeDiff(Path workspace);

    GitDiff stagedDiff(Path workspace);

    GitDiff commitDiff(Path workspace, String commit);

    List<GitBranch> branches(Path workspace);

    GitCommitResult commit(Path workspace, String message);

    default GitRestoreResult restoreWorkspace(Path workspace, GitSnapshot baseline, GitSnapshot current) {
        return GitRestoreResult.unavailable();
    }
}
