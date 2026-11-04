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
import java.util.ArrayList;
import java.util.Arrays;
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

        var result = runApproved(runner, "ENGINEERING_REQUEST_V1\nImplement the feature.");

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

        runApproved(runner, "ENGINEERING_REQUEST_V1\nFail this.");

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

        runApproved(runner, "ENGINEERING_REQUEST_V1\nRun verification.");

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

        runApproved(runner, "Make the requested change.");

        assertTrue(runner.history().list().isEmpty());
    }

    @Test
    void persistsGitBaselineAndPostRunStateWithTheEngineeringHistory() throws Exception {
        Files.writeString(project.resolve("pom.xml"), "<project/>");
        runGit("init", "-b", "main");
        runGit("config", "user.name", "EngineOS Test");
        runGit("config", "user.email", "engineos@example.test");
        runGit("add", "pom.xml");
        runGit("commit", "-m", "baseline");

        var history = new FileEngineeringHistoryRepository(project.resolve("history"));
        var runner = new EngineeringSessionRunner(
                new StubCoder(new CodingResult(0, "raw", "", Duration.ZERO)),
                new StubObservation(new ObservationRunner.ObservationResult(0, "observed", "")),
                history);

        var result = runApproved(runner, "ENGINEOS_REQUEST_V1\nInvestigate the repository.");
        var source = history.get(result.engineeringResult().history().runId()).orElseThrow().sourceControl();

        assertEquals("Git", source.provider());
        assertEquals("main", source.before().branch());
        assertEquals(source.before().headSha(), source.after().headSha());
        assertTrue(source.before().clean());
        assertTrue(source.after().clean());
    }

    @Test
    void explicitCommitStoresCommitMetadataOnTheSameHistoryRecord() throws Exception {
        Files.writeString(project.resolve("pom.xml"), "<project/>");
        runGit("init", "-b", "main");
        runGit("config", "user.name", "EngineOS Test");
        runGit("config", "user.email", "engineos@example.test");
        runGit("add", "pom.xml");
        runGit("commit", "-m", "baseline");

        var history = new FileEngineeringHistoryRepository(project.resolve("history"));
        var runner = new EngineeringSessionRunner(
                new StubCoder(new CodingResult(0, "raw", "", Duration.ZERO)),
                new StubObservation(new ObservationRunner.ObservationResult(0, "observed", "")),
                history);
        var result = runApproved(runner, "ENGINEOS_REQUEST_V1\nImplement the repository change.");
        Files.writeString(project.resolve("change.txt"), "approved\n");
        runGit("add", "change.txt");

        var commit = runner.commit(project, result.engineeringResult().history().runId(),
                "Implement repository change");
        var saved = history.get(result.engineeringResult().history().runId()).orElseThrow();

        assertTrue(commit.succeeded(), commit.error());
        assertEquals(commit.commitHash(), saved.sourceControl().commitSha());
        assertEquals("Implement repository change", saved.sourceControl().commitMessage());
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

        runApproved(runner, "ENGINEERING_REQUEST_V1\nFirst run.");
        runApproved(runner, "ENGINEERING_REQUEST_V1\nSecond run.");

        assertEquals(2, history.list().size());
        assertTrue(history.list().stream()
                .anyMatch(record -> record.engineeringRequest().endsWith("First run.")));
        assertTrue(history.list().stream()
                .anyMatch(record -> record.engineeringRequest().endsWith("Second run.")));
        assertTrue(history.list().stream()
                .noneMatch(record -> record.engineeringRequest().equals("Inspect the loaded workspace")));
    }

    private void runGit(String... arguments) throws Exception {
        var command = new ArrayList<String>();
        command.add("git");
        command.addAll(Arrays.asList(arguments));
        Process process = new ProcessBuilder(command)
                .directory(project.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        assertEquals(0, process.waitFor(), output);
    }

    private EngineeringSessionRunner.SessionResult runApproved(
            EngineeringSessionRunner runner, String prompt) {
        String requestId = java.util.UUID.randomUUID().toString();
        var proposal = runner.propose(requestId, project, prompt, "test-model", null,
                state -> { }, activity -> { });
        assertEquals(com.engineos.domain.EngineeringTask.Status.PROPOSED, proposal.status());
        return runner.approve(requestId, state -> { }, activity -> { });
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
