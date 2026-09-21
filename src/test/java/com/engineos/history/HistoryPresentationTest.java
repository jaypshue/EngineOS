package com.engineos.history;

import com.engineos.agent.AiUsage;
import com.engineos.domain.CopilotResultDetails;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryPresentationTest {
    @Test
    void extractsGoalAsDisplaySummaryWithoutChangingFullRequest() {
        String request = """
                ENGINEOS_REQUEST_V1
                ENGINEERING REQUEST
                Goal:
                Verify durable Engineering History end-to-end
                Details: preserve all fields.
                """;
        var record = record(request, "old summary");

        assertEquals("Verify durable Engineering History end-to-end", record.displaySummary());
        assertEquals(request, record.engineeringRequest());
        assertTrue(HistoryPresentation.format(record).contains(request));
    }

    @Test
    void usesSafeSummaryFallbackAndFormatsLegacyNullFields() {
        var record = record("A short request", "");

        assertEquals("A short request", record.displaySummary());
        assertTrue(HistoryPresentation.format(record).contains("AI USAGE (actual)"));
        assertTrue(HistoryPresentation.format(record).contains("Changes: +1"));
    }

    @Test
    void reportsFailedVerificationAsInsufficientWithItsDiagnostic() {
        var observation = new com.engineos.observation.ObservationRunner.ObservationResult(
                7, "Customer 1 not found", "Customer 1 not found",
                "cmd.exe /d /c mvnw.cmd -q test", Duration.ofSeconds(2));
        var record = EngineeringHistoryRecord.from(
                "run-failed",
                Instant.parse("2026-09-18T21:00:00Z"),
                Instant.parse("2026-09-18T21:01:00Z"),
                "ENGINEOS_REQUEST_V1\nVerify the change.",
                "Verify the change.",
                "GitHub Copilot",
                "github",
                "gpt-5.6-luna",
                "C:\\projects\\EngineOS",
                "MAVEN",
                "FAILED",
                "Coder completed successfully",
                0,
                "session-1",
                new AiUsage("gpt-5.6-luna", "1", null, null, null, null, null, null),
                new CopilotResultDetails(true, "gpt-5.6-luna", 1L, 2L,
                        1, 0, 1, List.of("src/App.java")),
                "result",
                "FAILED",
                false,
                null,
                "MAVEN",
                observation);

        String formatted = HistoryPresentation.format(record);

        assertTrue(formatted.contains("Evidence: insufficient"));
        assertTrue(formatted.contains("Verification failed: Customer 1 not found"));
        assertTrue(formatted.contains("mvnw.cmd"));
        assertTrue(formatted.contains("Exit code: 7"));
        assertTrue(formatted.contains("Status: FAILED"));
    }

    @Test
    void derivesCompleteVerifiedAssessmentAndStructuredHandoff() {
        var record = record("ENGINEOS_REQUEST_V1\nGoal:\nShip the feature.", "Ship the feature.");

        var assessment = EngineeringAssessment.from(record);
        assertEquals("COMPLETE", assessment.implementationStatus());
        assertEquals("VERIFIED", assessment.verificationStatus());
        assertEquals("SUFFICIENT", assessment.evidenceStatus());
        assertTrue(assessment.keyFinding().contains("verification passed"));

        String handoff = HistoryPresentation.handoff(record);
        assertTrue(handoff.startsWith("ENGINEERING RESULT"));
        assertTrue(handoff.contains("ORIGINAL ENGINEOS_REQUEST_V1"));
        assertTrue(handoff.contains("Ship the feature."));
        assertTrue(handoff.contains("ACTUAL FINAL WORKSPACE CHANGES"));
        assertTrue(handoff.contains("Implementation status: COMPLETE"));
        assertTrue(handoff.contains("Verification status: VERIFIED"));
        assertTrue(handoff.contains("Files: [file.txt]"));
    }

    @Test
    void doesNotTreatSuccessfulObservationWithoutAcceptanceEvidenceAsVerified() {
        var record = EngineeringHistoryRecord.from(
                "run-insufficient",
                Instant.parse("2026-09-18T21:00:00Z"),
                Instant.parse("2026-09-18T21:01:00Z"),
                """
                ENGINEOS_REQUEST_V1
                ACCEPTANCE CRITERIA:
                - endpoint returns customer data
                """,
                "Verify endpoint",
                "GitHub Copilot", "github", "gpt-5.6-luna", "C:\\projects\\EngineOS",
                "MAVEN", "GREEN", "Coder completed successfully", 0, "session-1",
                new AiUsage("gpt-5.6-luna", "1", null, null, null, null, null, null),
                new CopilotResultDetails(true, "gpt-5.6-luna", 1L, 2L,
                        1, 0, 1, List.of("src/App.java")),
                "result", "SUCCEEDED", true, null, "MAVEN",
                new com.engineos.observation.ObservationRunner.ObservationResult(
                        0, "", "", "mvnw.cmd -q test", Duration.ofSeconds(1)));

        var assessment = EngineeringAssessment.from(record);

        assertEquals("INSUFFICIENT_EVIDENCE", assessment.verificationStatus());
        assertEquals("INSUFFICIENT", assessment.evidenceStatus());
        assertTrue(assessment.uncertainties().stream()
                .anyMatch(value -> value.contains("does not demonstrate")));
    }

    @Test
    void retainsVerificationFailureDiagnostic() {
        var observation = new com.engineos.observation.ObservationRunner.ObservationResult(
                7, "Customer 1 not found", "Customer 1 not found",
                "mvnw.cmd -q test", Duration.ofSeconds(2));
        var record = EngineeringHistoryRecord.from(
                "run-failed-2", Instant.parse("2026-09-18T21:00:00Z"),
                Instant.parse("2026-09-18T21:01:00Z"), "Verify the change.", "Verify the change.",
                "GitHub Copilot", "github", "gpt-5.6-luna", "C:\\projects\\EngineOS",
                "MAVEN", "FAILED", "Coder completed successfully", 0, "session-1",
                AiUsage.unavailable(),
                new CopilotResultDetails(true, "gpt-5.6-luna", 1L, 2L,
                        1, 0, 1, List.of("src/App.java")),
                "result", "FAILED", false, null, "MAVEN", observation);

        var assessment = EngineeringAssessment.from(record);

        assertEquals("FAILED", assessment.verificationStatus());
        assertTrue(assessment.keyFinding().contains("Customer 1 not found"));
    }

    @Test
    void handoffPreservesDiagnosticsAndExcludesObservationArtifactLocation() {
        var observation = new com.engineos.observation.ObservationRunner.ObservationResult(
                1, "baseline compilation failed", "cannot find symbol",
                "mvnw.cmd -q test", Duration.ofSeconds(2));
        var record = EngineeringHistoryRecord.from(
                "run-blocked",
                Instant.parse("2026-09-18T21:00:00Z"),
                Instant.parse("2026-09-18T21:01:00Z"),
                "ENGINEOS_REQUEST_V1\nImplement the change.",
                "Implement the change.",
                "GitHub Copilot", "github", "gpt-5.6-luna", "C:\\projects\\EngineOS",
                "MAVEN", "FAILED", "Coder completed successfully", 0, "session-1",
                AiUsage.unavailable(),
                new CopilotResultDetails(true, "gpt-5.6-luna", 1L, 2L,
                        1, 0, 1, List.of("src/App.java")),
                "result", "FAILED", true, "C:\\observations\\heavy.zip", "MAVEN", observation);

        var assessment = EngineeringAssessment.from(record);
        assertEquals("COMPLETE", assessment.implementationStatus());
        assertEquals("BLOCKED", assessment.verificationStatus());
        assertEquals("INSUFFICIENT", assessment.evidenceStatus());
        assertTrue(assessment.keyFinding().contains("cannot find symbol"));

        String handoff = HistoryPresentation.handoff(record);
        assertTrue(handoff.contains("mvnw.cmd -q test"));
        assertTrue(handoff.contains("Exit code: 1"));
        assertTrue(handoff.contains("cannot find symbol"));
        assertFalse(handoff.contains("heavy.zip"));
    }

    @Test
    void formatsPersistedActivityAndIncludesItInCopiedHandoff() {
        var observation = new com.engineos.observation.ObservationRunner.ObservationResult(
                0, "passed", "", "mvnw.cmd -q test", Duration.ofSeconds(1));
        var record = EngineeringHistoryRecord.from(
                "activity-run", Instant.parse("2026-09-18T21:00:00Z"),
                Instant.parse("2026-09-18T21:01:00Z"), "ENGINEERING_REQUEST_V1\nDo work.",
                "Do work.", "GitHub Copilot", "github", "gpt-5.6-luna",
                "C:\\projects\\EngineOS", "MAVEN", "GREEN", "Coder completed successfully",
                0, "session-1", AiUsage.unavailable(), null, "result", "SUCCEEDED",
                true, null, "MAVEN", observation,
                List.of("Inspecting files", "Editing src/App.java...", "Copilot completed."));

        String formatted = HistoryPresentation.format(record);
        String handoff = HistoryPresentation.handoff(record);

        assertTrue(formatted.contains("CODING AGENT ACTIVITY"));
        assertTrue(formatted.contains("- Inspecting files"));
        assertTrue(handoff.contains("CODING AGENT ACTIVITY"));
        assertTrue(handoff.contains("- Editing src/App.java..."));
    }

    @Test
    void presentsLegacyRecordWithoutActivityAsReadableEmptyState() {
        var formatted = HistoryPresentation.format(record("legacy request", "legacy"));
        var handoff = HistoryPresentation.handoff(record("legacy request", "legacy"));

        assertTrue(formatted.contains("No persisted Coding Agent activity was recorded"));
        assertTrue(handoff.contains("This may be a legacy history record."));
    }

    private static EngineeringHistoryRecord record(String request, String summary) {
        return new EngineeringHistoryRecord(
                "run-1", Instant.parse("2026-09-18T21:00:00Z"),
                Instant.parse("2026-09-18T21:01:00Z"), Duration.ofMinutes(1),
                request, summary, "GitHub Copilot", "github", "gpt-5.6-luna",
                "gpt-5.6-luna", "C:\\projects\\EngineOS", "ENGINEOS", "GREEN",
                "Coder completed successfully", 0, "session-1",
                new AiUsage("gpt-5.6-luna", "1", null, null, null, null, null, null),
                100L, 200L, 1, 0, 1, List.of("file.txt"), true, true,
                "result", "SUCCEEDED", true, null, "verified",
                "mvnw.cmd -q test", 0, Duration.ofSeconds(1),
                "Acceptance criteria verified: feature works.", "");
    }
}
