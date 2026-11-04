package com.engineos.agent;

import com.engineos.domain.EngineeringRequest;
import com.engineos.domain.ExecutionPolicy;
import com.engineos.domain.WorkspaceContext;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

final class EngineeringProposalContext {
    private EngineeringProposalContext() {
    }

    static List<String> files(WorkspaceContext workspace) {
        Path root = workspace.root().toAbsolutePath().normalize();
        return workspace.discoveredFiles().stream()
                .map(path -> path.toAbsolutePath().normalize())
                .filter(path -> path.startsWith(root))
                .map(root::relativize)
                .filter(EngineeringProposalContext::relevant)
                .map(Path::toString).sorted().toList();
    }

    private static boolean relevant(Path path) {
        for (Path part : path) {
            String name = part.toString().toLowerCase(Locale.ROOT);
            if (name.startsWith(".") || List.of("target", "build", "node_modules", "dist",
                    "bin", "obj", "observations", "history").contains(name)) {
                return false;
            }
        }
        return true;
    }

    static String projectType(WorkspaceContext workspace) {
        var files = files(workspace);
        var kinds = new java.util.ArrayList<String>();
        if (has(files, "pom.xml")) kinds.add("Java/Maven");
        if (has(files, "build.gradle") || has(files, "build.gradle.kts")) kinds.add("JVM/Gradle");
        if (has(files, "package.json")) kinds.add("JavaScript/TypeScript (package.json)");
        if (has(files, "pyproject.toml") || has(files, "requirements.txt")) kinds.add("Python");
        if (has(files, "go.mod")) kinds.add("Go");
        if (has(files, "Cargo.toml")) kinds.add("Rust/Cargo");
        if (files.stream().anyMatch(file -> file.endsWith(".csproj"))) kinds.add(".NET");
        return kinds.isEmpty() ? "Unknown; establish from existing files" : String.join(", ", kinds);
    }

    private static boolean has(List<String> files, String name) {
        return files.stream().anyMatch(file -> Path.of(file).getFileName().toString().equals(name));
    }

    static String describe(WorkspaceContext workspace) {
        var files = files(workspace);
        return "Repository: " + workspace.repositoryName()
                + "\nProject type (inferred from file names only): " + projectType(workspace)
                + "\nGit branch: " + (workspace.gitBranch().isBlank() ? "unavailable" : workspace.gitBranch())
                + "\nGit worktree clean: " + workspace.gitWorktreeClean()
                + "\nFile inventory (at most 60 of " + files.size() + " eligible files; no contents read):\n"
                + files.stream().limit(60).map(file -> file.length() > 200 ? file.substring(0, 200) : file)
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    static String constraints(WorkspaceContext workspace, EngineeringRequest request) {
        var policy = ExecutionPolicy.forRequest(request);
        return "Authoritative EngineOS boundary: phase=" + policy.phase()
                + "; " + (policy.workspaceModificationAllowed()
                ? "only request-related changes in the selected workspace may be proposed."
                : "workspace modifications are prohibited.")
                + " No execution before explicit human approval. Preserve existing work."
                + "\nWorkspace: " + workspace.root()
                + "\nGit baseline: branch=" + workspace.gitBranch()
                + ", clean=" + workspace.gitWorktreeClean()
                + "\nHuman constraints: " + request.sections().getOrDefault("CONSTRAINTS",
                "All restrictions in the original request remain binding.");
    }

    static String verification(WorkspaceContext workspace) {
        String type = projectType(workspace);
        return "Inspect existing verification configuration for " + type
                + "; use the smallest relevant existing tests or observation target. "
                + "Do not invent commands or claim checks have run. Report results and evidence gaps.";
    }
}
