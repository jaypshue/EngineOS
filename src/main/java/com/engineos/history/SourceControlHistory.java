package com.engineos.history;

import com.engineos.infrastructure.git.GitSnapshot;

import java.util.List;

public record SourceControlHistory(
        String provider,
        String repositoryRoot,
        State before,
        State after,
        String commitSha,
        String commitMessage) {
    public SourceControlHistory {
        provider = provider == null || provider.isBlank() ? "Git" : provider;
        commitSha = commitSha == null ? "" : commitSha;
        commitMessage = commitMessage == null ? "" : commitMessage;
    }

    public static SourceControlHistory from(GitSnapshot before, GitSnapshot after) {
        if (before == null && after == null) {
            return null;
        }
        GitSnapshot reference = before == null ? after : before;
        return new SourceControlHistory(
                "Git",
                reference == null || reference.repositoryRoot() == null
                        ? "" : reference.repositoryRoot().toString(),
                State.from(before),
                State.from(after),
                "",
                "");
    }

    public SourceControlHistory withCommit(String sha, String message) {
        return new SourceControlHistory(provider, repositoryRoot, before, after, sha, message);
    }

    public record State(
            String branch,
            String headSha,
            boolean clean,
            List<String> changedFiles,
            int linesAdded,
            int linesRemoved) {
        public State {
            branch = branch == null ? "" : branch;
            headSha = headSha == null ? "" : headSha;
            changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
        }

        private static State from(GitSnapshot snapshot) {
            if (snapshot == null) {
                return null;
            }
            return new State(
                    snapshot.branch(),
                    snapshot.headSha(),
                    snapshot.clean(),
                    snapshot.changes().stream().map(change -> change.path()).toList(),
                    snapshot.linesAdded(),
                    snapshot.linesRemoved());
        }
    }
}
