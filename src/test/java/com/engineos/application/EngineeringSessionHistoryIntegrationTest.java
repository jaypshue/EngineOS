package com.engineos.application;

import com.engineos.agent.AgentProvider;
import com.engineos.agent.AiUsage;
import com.engineos.agent.ICodingAgent;
import com.engineos.domain.CodingResult;
import com.engineos.domain.CodingTask;
import com.engineos.domain.CopilotResultDetails;
import com.engineos.domain.WorkspaceContext;
import com.engineos.history.EngineeringHistoryRecord;
import com.engineos.history.FileEngineeringHistoryRepository;
import com.engineos.observation.ObservationRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineeringSessionHistoryIntegrationTest {
    @TempDir
    Path project;

    @Test
    void terminalRunCreatesOneRecordWithCodingAndObservationDistinguished() {
        try {
            Files.writeString(project.resolve("pom.xml"), "<project/>");
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        var history = new FileEngineeringHistoryRepository(project.resolve("history"));
        var coding = new CodingResult(0, "raw output", "", Duration.ofSeconds(2),
                "requested-model", new AiUsage("actual-model", "4", "5", "6", null, null, null, null),
                "session-42",
                new CopilotResultDetails(true, "actual-model", 100L, 200L,
                        3, 1, 1, List.of("src/App.java")));
        var runner = new EngineeringSessionRunner(
                new StubCoder(coding),
                new StubObservation(new ObservationRunner.ObservationResult(
                        1, "Customer 1 not found", "Customer 1 not found",
                        "mvnw.cmd -q test", Duration.ofSeconds(3))),
                history);

        var result = runner.run(project, "ENGINEERING_REQUEST_V1\nImplement the feature.", state -> {
        });

        assertEquals(EngineeringSessionRunner.SessionState.FAILED, result.state());
        var saved = history.list();
        assertEquals(1, saved.size());
        EngineeringHistoryRecord record = saved.getFirst();
        assertEquals("ENGINEERING_REQUEST_V1\nImplement the feature.", record.engineeringRequest());
        assertEquals("actual-model", record.actualModel());
        assertEquals("session-42", record.sessionId());
        assertEquals(3, record.linesAdded());
        assertEquals(List.of("src/App.java"), record.modifiedFiles());
        assertEquals("FAILED", record.finalStatus());
        assertTrue(record.codingResultStatus().contains("completed successfully"));
        assertEquals("FAILED", record.observationStatus());
        assertTrue(record.semanticResultComplete());
        assertEquals("mvnw.cmd -q test", record.verificationCommand());
        assertEquals(1, record.verificationExitCode());
        assertEquals("Customer 1 not found", record.verificationError());
        assertTrue(record.verificationOutput().contains("Customer 1 not found"));
        assertTrue(record.activity().stream()
                .anyMatch(entry -> entry.contains("Workflow guidance")));

        var handoff = result.engineeringResult();
        assertEquals("ENGINEERING_REQUEST_V1\nImplement the feature.", handoff.request());
        assertEquals("requested-model", handoff.execution().requestedModel());
        assertEquals("actual-model", handoff.execution().actualModel());
        assertEquals("session-42", handoff.execution().sessionId());
        assertEquals(3, handoff.changes().linesAdded());
        assertEquals(List.of("src/App.java"), handoff.changes().modifiedFiles());
        assertEquals("FAILED", handoff.verification().status());
        assertEquals(record.runId(), handoff.history().runId());
        assertFalse(handoff.supervision().evidenceSufficient());
        assertEquals("FAILED", handoff.supervision().status());
        assertEquals("IMPLEMENTED", handoff.supervision().implementationStatus());
        assertTrue(handoff.supervision().uncertainties().stream()
                .anyMatch(gap -> gap.contains("Targeted verification failed")));
        assertTrue(handoff.activity().stream()
                .anyMatch(entry -> entry.contains("Targeted verification failed")));
    }

    @Test
    void failedCodingIsNotRecordedAsSuccessful() {
        try {
            Files.writeString(project.resolve("pom.xml"), "<project/>");
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        var history = new FileEngineeringHistoryRepository(project.resolve("history"));
        var runner = new EngineeringSessionRunner(
                new StubCoder(new CodingResult(7, "", "failed", Duration.ZERO)),
                new StubObservation(new ObservationRunner.ObservationResult(0, "not used", "")),
                history);

        runner.run(project, "ENGINEERING_REQUEST_V1\nFail this.", state -> {
        });

        var record = history.list().getFirst();
        assertEquals("FAILED", record.finalStatus());
        assertEquals(7, record.exitCode());
        assertEquals("NOT_RUN", record.observationStatus());
        assertFalse(record.activity().isEmpty());
    }

    @Test
    void verificationIsAttachedToThePrimaryRecordWithoutCreatingAnotherRecord() {
        try {
            Files.writeString(project.resolve("pom.xml"), "<project/>");
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        var history = new FileEngineeringHistoryRepository(project.resolve("history"));
        var runner = new EngineeringSessionRunner(
                new StubCoder(new CodingResult(0, "raw", "", Duration.ZERO)),
                new StubObservation(new ObservationRunner.ObservationResult(0, "observed", "")),
                history);

        runner.run(project, "ENGINEERING_REQUEST_V1\nRun verification.", state -> {
        });

        assertEquals(1, history.list().size());
        assertEquals("SUCCEEDED", history.list().getFirst().observationStatus());
        assertTrue(history.list().getFirst().observationAvailable());
    }

    @Test
    void defaultRunnerDoesNotPersistInternalOrSyntheticRuns() {
        try {
            Files.writeString(project.resolve("pom.xml"), "<project/>");
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        var runner = new EngineeringSessionRunner(
                new StubCoder(new CodingResult(0, "raw", "", Duration.ZERO)),
                new StubObservation(new ObservationRunner.ObservationResult(0, "observed", "")));

        runner.run(project, "Make the requested change.", state -> {
        });

        assertTrue(runner.history().list().isEmpty());
    }

    @Test
    void independentPrimaryRunsCreateIndependentRecords() {
        try {
            Files.writeString(project.resolve("pom.xml"), "<project/>");
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        var history = new FileEngineeringHistoryRepository(project.resolve("history"));
        var runner = new EngineeringSessionRunner(
                new StubCoder(new CodingResult(0, "raw", "", Duration.ZERO)),
                new StubObservation(new ObservationRunner.ObservationResult(0, "observed", "")),
                history);

        runner.run(project, "ENGINEERING_REQUEST_V1\nFirst run.", state -> {
        });
        runner.run(project, "ENGINEERING_REQUEST_V1\nSecond run.", state -> {
        });

        assertEquals(2, history.list().size());
        assertTrue(history.list().stream()
                .anyMatch(record -> record.engineeringRequest().endsWith("First run.")));
        assertTrue(history.list().stream()
                .anyMatch(record -> record.engineeringRequest().endsWith("Second run.")));
        assertTrue(history.list().stream()
                .noneMatch(record -> record.engineeringRequest().equals("Inspect the loaded workspace")));
    }

    private static final class StubCoder implements ICodingAgent {
        private final CodingResult result;

        private StubCoder(CodingResult result) {
            this.result = result;
        }

        @Override
        public AgentProvider provider() {
            return new AgentProvider("test", "stub");
        }

        @Override
        public CodingResult execute(WorkspaceContext workspace, CodingTask task) {
            return result;
        }
    }

    private static final class StubObservation implements ObservationRunner {
        private final ObservationResult result;

        private StubObservation(ObservationResult result) {
            this.result = result;
        }

        @Override
        public ObservationResult run(Path project) {
            return result;
        }
    }
}
