package com.engineos.observation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerShellObservationRunnerTest {
    @TempDir
    Path tempDirectory;

    @Test
    void preservesMavenFailureCommandDurationAndDiagnostics() throws Exception {
        Files.writeString(tempDirectory.resolve("pom.xml"), "<project/>");
        Files.writeString(tempDirectory.resolve("mvnw.cmd"),
                "@echo off\r\n"
                        + "echo Customer 1 not found\r\n"
                        + "exit /b 7\r\n");

        var target = new VerificationTarget(
                VerificationTarget.Kind.MAVEN, tempDirectory, "Maven");
        var result = new PowerShellObservationRunner(
                tempDirectory, Duration.ofSeconds(10))
                .run(null, target);

        assertFalse(result.succeeded());
        assertEquals(7, result.exitCode());
        assertTrue(result.output().contains("Customer 1 not found"));
        assertTrue(result.error().contains("Customer 1 not found"));
        assertTrue(result.command().contains("mvnw.cmd"));
        assertTrue(result.duration().compareTo(Duration.ZERO) >= 0);
    }

    @Test
    void preservesSuccessfulMavenVerificationEvidence() throws Exception {
        Files.writeString(tempDirectory.resolve("pom.xml"), "<project/>");
        Files.writeString(tempDirectory.resolve("mvnw.cmd"),
                "@echo off\r\n"
                        + "echo Tests run: 1, Failures: 0, Errors: 0\r\n"
                        + "exit /b 0\r\n");

        var target = new VerificationTarget(
                VerificationTarget.Kind.MAVEN, tempDirectory, "Maven");
        var result = new PowerShellObservationRunner(
                tempDirectory, Duration.ofSeconds(10))
                .run(null, target);

        assertTrue(result.succeeded());
        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("Tests run: 1"));
        assertEquals("", result.error());
        assertTrue(result.command().contains("mvnw.cmd"));
        assertTrue(result.duration().compareTo(Duration.ZERO) >= 0);
    }

    @Test
    void existingDesktopObservationUsesBoundedTargetLookupAndPreservesTheTarget() throws Exception {
        Path script = Path.of("scripts", "capture-intassist-observation.ps1");
        String source = Files.readString(script);

        assertTrue(source.contains("[switch]$ObserveExistingTarget"));
        assertTrue(source.contains("Wait-ForTargetWindow -TimeoutSeconds 5"));
        assertTrue(source.contains("Existing $TargetKind window was verified and left running."));
        assertTrue(source.contains("if ($ownsTarget -and $targetProcessId -ne $null)"));
    }
}
