package com.engineos.observation;

import com.engineos.domain.WorkspaceContext;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VerificationTargetTest {
    @Test
    void resolvesEngineOsFromWorkspaceContents() {
        var workspace = workspace(
                Path.of("src", "main", "java", "com", "engineos", "EngineOsApplication.java"));

        var target = VerificationTarget.from(workspace);

        assertEquals(VerificationTarget.Kind.ENGINEOS, target.kind());
        assertEquals("EngineOS", target.windowTitle());
    }

    @Test
    void resolvesIntAssistFromWorkspaceContents() {
        var workspace = workspace(
                Path.of("src", "main", "java", "com", "example", "interview", "DesktopDemoMode.java"));

        var target = VerificationTarget.from(workspace);

        assertEquals(VerificationTarget.Kind.INTASSIST, target.kind());
        assertEquals("IntAssist", target.windowTitle());
    }

    @Test
    void resolvesGenericMavenRepositoryFromPom() {
        var workspace = workspace(Path.of("pom.xml"));

        var target = VerificationTarget.from(workspace);

        assertEquals(VerificationTarget.Kind.MAVEN, target.kind());
        assertEquals("Maven", target.windowTitle());
        assertEquals(
                java.util.List.of("cmd.exe", "/d", "/c", "mvn.cmd", "-q", "test"),
                target.verificationCommand());
    }

    @Test
    void rejectsUnsupportedWorkspaceWithoutGuessing() {
        assertThrows(IllegalArgumentException.class, () -> VerificationTarget.from(workspace(Path.of("README.md"))));
    }

    @Test
    void distinguishesMatchingAndMismatchingTargets() {
        var workspace = workspace(
                Path.of("src", "main", "java", "com", "example", "interview", "DesktopDemoMode.java"));
        var matching = new VerificationTarget(
                VerificationTarget.Kind.INTASSIST, workspace.root(), "IntAssist");
        var mismatching = new VerificationTarget(
                VerificationTarget.Kind.ENGINEOS, Path.of("C:\\projects\\EngineOS"), "EngineOS");

        assertEquals(
                VerificationTarget.from(workspace).kind(),
                matching.kind());
        assertEquals(VerificationTarget.Kind.ENGINEOS, mismatching.kind());
    }

    private static WorkspaceContext workspace(Path... files) {
        return new WorkspaceContext(Path.of("C:\\projects\\target"), "target", "main", true, List.of(files));
    }
}
