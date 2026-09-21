package com.engineos.observation;

import com.engineos.domain.WorkspaceContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public record VerificationTarget(Kind kind, Path workspace, String windowTitle) {
    public enum Kind {
        INTASSIST,
        ENGINEOS,
        MAVEN
    }

    public VerificationTarget {
        if (kind == null || workspace == null || windowTitle == null || windowTitle.isBlank()) {
            throw new IllegalArgumentException("Verification target details are required");
        }
    }

    public static VerificationTarget from(WorkspaceContext workspace) {
        var files = workspace.discoveredFiles().stream()
                .map(Path::toString)
                .map(String::toLowerCase)
                .toList();
        if (files.stream().anyMatch(path -> path.endsWith("com\\engineos\\engineosapplication.java"))) {
            return new VerificationTarget(Kind.ENGINEOS, workspace.root(), "EngineOS");
        }
        if (files.stream().anyMatch(path -> path.endsWith("desktopdemomode.java"))) {
            return new VerificationTarget(Kind.INTASSIST, workspace.root(), "IntAssist");
        }
        if (files.stream().anyMatch(path -> path.endsWith("pom.xml"))) {
            return new VerificationTarget(Kind.MAVEN, workspace.root(), "Maven");
        }
        throw new IllegalArgumentException(
                "No supported verification target was found in workspace: " + workspace.root());
    }

    public List<String> verificationCommand() {
        if (kind != Kind.MAVEN) {
            throw new IllegalStateException("Verification command is only available for Maven targets");
        }
        Path wrapper = workspace.resolve("mvnw.cmd");
        if (Files.isRegularFile(wrapper)) {
            return List.of("cmd.exe", "/d", "/c", wrapper.toString(), "-q", "test");
        }
        return List.of("cmd.exe", "/d", "/c", "mvn.cmd", "-q", "test");
    }
}
