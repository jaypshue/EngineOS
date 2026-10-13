package com.engineos.observation;

import com.engineos.domain.EngineeringResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservationEvidencePackageWriterTest {
    @TempDir
    Path tempDirectory;

    @Test
    void addsRequestPlanExecutionAndResultToCompletedPackage() throws Exception {
        Path packageDirectory = tempDirectory.resolve("20260919T220000Z");
        Files.createDirectories(packageDirectory);
        Files.writeString(packageDirectory.resolve("manifest.json"),
                "\uFEFF{\"result\":{\"exitCode\":0,\"succeeded\":true}}");
        String request = """
                ENGINEOS_REQUEST_V1

                Goal:
                Verify the API.

                Acceptance Criteria:
                - readiness returns 200
                """;
        var observation = new ObservationRunner.ObservationResult(
                0, "Observation package: " + packageDirectory, "");
        var result = new EngineeringResult(
                request, null, java.util.List.of("activity"), null, null, null, null,
                new EngineeringResult.Supervision("VERIFIED", true, java.util.List.of(), "IMPLEMENTED"));

        ObservationEvidencePackageWriter.writeIfAvailable(observation, request, result);

        assertTrue(Files.isRegularFile(packageDirectory.resolve("engineering-request.txt")));
        assertTrue(Files.isRegularFile(packageDirectory.resolve("engineering-result.json")));
        assertTrue(Files.isRegularFile(packageDirectory.resolve("verification-plan.json")));
        assertTrue(Files.isRegularFile(packageDirectory.resolve("verification-execution.txt")));
        assertTrue(Files.isRegularFile(packageDirectory.resolve("observation/result.json")));
        assertTrue(Files.readString(packageDirectory.resolve("manifest.json"))
                .contains("engineeringResult"));
    }

    @Test
    void writesExecutionPolicyWithEngineeringResult() throws Exception {
        Path packageDirectory = tempDirectory.resolve("20260919T220001Z");
        Files.createDirectories(packageDirectory);
        Files.writeString(packageDirectory.resolve("manifest.json"),
                "{\"result\":{\"exitCode\":0,\"succeeded\":true}}");
        var control = new com.engineos.domain.ExecutionControl(
                com.engineos.domain.ExecutionPhase.VERIFICATION,
                new com.engineos.domain.ExecutionPolicy(
                        com.engineos.domain.ExecutionPhase.VERIFICATION,
                        Duration.ofSeconds(2), 1, false, true),
                Instant.now(), Instant.now(), "TIMED_OUT", false, java.util.List.of(), 0,
                java.util.List.of(com.engineos.domain.ExecutionPhase.VERIFICATION));
        var result = new EngineeringResult(
                "ENGINEOS_REQUEST_V1",
                new EngineeringResult.Execution("agent", "model", "model", null,
                        Duration.ofSeconds(1), com.engineos.agent.AiUsage.unavailable()),
                java.util.List.of(), null, null, null, null,
                new EngineeringResult.Supervision("FAILED", false, java.util.List.of("timeout"), "NOT_IMPLEMENTED"),
                control);

        ObservationEvidencePackageWriter.writeIfAvailable(
                new ObservationRunner.ObservationResult(0, "Observation package: " + packageDirectory, ""),
                result.request(), result);

        assertTrue(Files.isRegularFile(packageDirectory.resolve("execution-policy.json")));
        assertTrue(Files.readString(packageDirectory.resolve("manifest.json"))
                .contains("executionPolicy"));
    }

    @Test
    void createsRepositoryPackageWhenObservationHasNoExternalPackagePath() throws Exception {
        var result = new EngineeringResult(
                "Investigate the repository.",
                null,
                java.util.List.of(),
                null,
                null,
                null,
                new EngineeringResult.History("repository-run", true),
                new EngineeringResult.Supervision(
                        "INSUFFICIENT_EVIDENCE", false, java.util.List.of(), "INVESTIGATED"));

        ObservationEvidencePackageWriter.writeIfAvailable(
                new ObservationRunner.ObservationResult(0, "Repository investigation completed.", ""),
                result.request(),
                result,
                "Architecture and data-flow findings.",
                tempDirectory);

        var packageDirectory = new ObservationPackageService(tempDirectory)
                .findCompletedForRun("repository-run")
                .orElseThrow()
                .directory();
        assertTrue(Files.readString(packageDirectory.resolve("coding-agent-findings.txt"))
                .contains("Architecture"));
        assertTrue(Files.readString(packageDirectory.resolve("manifest.json"))
                .contains("repository-run"));
    }
}
