package com.engineos.application;

import com.engineos.agent.AgentProvider;
import com.engineos.agent.ICodingAgent;
import com.engineos.domain.CodingResult;
import com.engineos.domain.CodingTask;
import com.engineos.domain.WorkspaceContext;
import com.engineos.observation.ObservationRunner;
import com.engineos.observation.VerificationTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineeringSessionRunnerTest {
    @TempDir
    Path project;

    @Test
    void localServiceExposesExecutionWithoutLeakingRunnerTypes() {
        var observation = new RecordingObservationRunner(
                new ObservationRunner.ObservationResult(0, "package", ""));
        EngineOsService service = new LocalEngineOsService(new EngineeringSessionRunner(
                new StubCoder(new CodingResult(0, "coded", "", Duration.ZERO)),
                observation));
        var states = new ArrayList<EngineOsService.ExecutionState>();

        var result = service.submit(project, "Make the requested change.", "selected-model",
                states::add, activity -> { });

        assertEquals(EngineOsService.ExecutionState.GREEN, result.state());
        assertEquals(List.of(
                EngineOsService.ExecutionState.STARTING,
                EngineOsService.ExecutionState.CODING_AGENT_WORKING,
                EngineOsService.ExecutionState.RUNNING_OBSERVATION,
                EngineOsService.ExecutionState.GREEN), states);
        assertTrue(result.engineeringResult() != null);
        assertTrue(observation.called);
    }

    @Test
    void reachesGreenAfterCodingAndObservationSucceed() {
        var observation = new RecordingObservationRunner(
                new ObservationRunner.ObservationResult(0, "package", ""));
        var runner = new EngineeringSessionRunner(
                new StubCoder(new CodingResult(0, "coded", "", Duration.ZERO)),
                observation);
        var states = new ArrayList<EngineeringSessionRunner.SessionState>();

        var result = runner.run(project, "Make the requested change.", states::add);

        assertEquals(EngineeringSessionRunner.SessionState.GREEN, result.state());
        assertEquals(List.of(
                EngineeringSessionRunner.SessionState.STARTING,
                EngineeringSessionRunner.SessionState.CODING_AGENT_WORKING,
                EngineeringSessionRunner.SessionState.RUNNING_OBSERVATION,
                EngineeringSessionRunner.SessionState.GREEN), states);
        assertTrue(observation.called);
    }

    @Test
    void forwardsExplicitModelAndRetainsCodingUsage() {
        var observation = new RecordingObservationRunner(
                new ObservationRunner.ObservationResult(0, "package", ""));
        var coder = new ModelRecordingCoder(new CodingResult(0, "coded", "", Duration.ZERO,
                "actual-model", new com.engineos.agent.AiUsage("actual-model", "2", "3", "4", "5", "6", null, null)));
        var runner = new EngineeringSessionRunner(coder, observation);

        var result = runner.run(project, "Make the requested change.", "selected-model",
                state -> { }, activity -> { });

        assertEquals("selected-model", coder.selectedModel);
        assertEquals("actual-model", result.coding().usage().model());
        assertEquals("2", result.coding().usage().aiCredits());
    }

    @Test
    void retainsActualSessionIdInTheEngineeringResult() {
        var observation = new RecordingObservationRunner(
                new ObservationRunner.ObservationResult(0, "package", ""));
        var coding = new CodingResult(0, "coded", "", Duration.ZERO, "actual-model",
                new com.engineos.agent.AiUsage("actual-model", "2", "3", "4", "5", "6", null, null),
                "session-a");
        var runner = new EngineeringSessionRunner(new StubCoder(coding), observation);

        var result = runner.run(project, "Make the requested change.", "selected-model",
                state -> { }, activity -> { });

        assertEquals("session-a", result.coding().sessionId());
    }

    @Test
    void usesFinalGitChangesConsistentlyAcrossResultAndControl() throws Exception {
        Files.writeString(project.resolve("pom.xml"), "<project/>");
        Path source = project.resolve("src/Main.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "class Main {}\n");
        runGit("init");
        runGit("config", "user.email", "engineos-test@example.invalid");
        runGit("config", "user.name", "EngineOS Test");
        runGit("add", ".");
        runGit("commit", "-m", "baseline");

        var runner = new EngineeringSessionRunner(
                new MutatingCoder(new CodingResult(0, "coded", "", Duration.ZERO)),
                new RecordingObservationRunner(
                        new ObservationRunner.ObservationResult(0, "verified", "")));

        var result = runner.run(project, "ENGINEOS_REQUEST_V1\nImplement the change.", state -> { });
        var engineering = result.engineeringResult();

        assertEquals(engineering.changes().modifiedFiles(),
                engineering.control().changedFiles());
        assertEquals(engineering.changes().filesModified(),
                engineering.control().changedFiles().size());
        assertTrue(engineering.assessment().actualChanges().contains("src/Main.java"));
    }

    @Test
    void stopsAtFailedCodingWithoutRunningObservation() {
        var observation = new RecordingObservationRunner(
                new ObservationRunner.ObservationResult(0, "package", ""));
        var runner = new EngineeringSessionRunner(
                new StubCoder(new CodingResult(1, "", "agent failed", Duration.ZERO)),
                observation);
        var states = new ArrayList<EngineeringSessionRunner.SessionState>();

        var result = runner.run(project, "Make the requested change.", states::add);

        assertEquals(EngineeringSessionRunner.SessionState.FAILED, result.state());
        assertEquals(EngineeringSessionRunner.SessionState.FAILED, states.getLast());
        assertFalse(observation.called);
    }

    @Test
    void recordsVerificationPolicyAndTerminationInEngineeringResult() {
        try {
            createMarker("src/main/java/com/engineos/EngineOsApplication.java");
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        var observation = new RecordingObservationRunner(
                new ObservationRunner.ObservationResult(0, "package", ""));
        var runner = new EngineeringSessionRunner(
                new StubCoder(new CodingResult(0, "coded", "", Duration.ZERO)),
                observation);

        var result = runner.run(project, """
                ENGINEOS_REQUEST_V1
                Goal:
                Verify the existing workflow.
                Constraints:
                Do not change source files.
                Verification:
                Verification only.
                """, state -> { });

        assertTrue(result.engineeringResult().control() != null);
        assertEquals(com.engineos.domain.ExecutionPhase.VERIFICATION,
                result.engineeringResult().control().phase());
        assertFalse(result.engineeringResult().control().policy().workspaceModificationAllowed());
        assertEquals("COMPLETED", result.engineeringResult().control().termination());
        assertTrue(observation.existingTarget);
    }

    @Test
    void verificationOnlySkipsCodingAgentAndDelegatesBehaviorToObservation() throws Exception {
        createMarker("src/main/java/com/engineos/EngineOsApplication.java");
        var observation = new RecordingObservationRunner(
                new ObservationRunner.ObservationResult(0, "deterministic UI evidence", ""));
        var coder = new StubCoder(new CodingResult(0, "must not run", "", Duration.ZERO));
        var runner = new EngineeringSessionRunner(coder, observation);

        var result = runner.run(project, """
                ENGINEOS_REQUEST_V1
                Goal:
                Verify the existing EngineOS Desktop behavior.
                Verification only.
                Do not change source files.
                """, state -> { });

        assertEquals(EngineeringSessionRunner.SessionState.GREEN, result.state());
        assertFalse(coder.called);
        assertTrue(observation.existingTarget);
        assertTrue(result.coding().standardOutput().contains("Coding Agent skipped"));
    }

    private static final class ModelRecordingCoder extends StubCoder {
        private String selectedModel;

        private ModelRecordingCoder(CodingResult result) {
            super(result);
        }

        @Override
        public CodingResult execute(WorkspaceContext workspace, CodingTask task, String model,
                                    Consumer<String> activitySink) {
            selectedModel = model;
            return super.execute(workspace, task);
        }
    }

    @Test
    void forwardsCodingAgentActivityWhileTheRequestRuns() {
        var observation = new RecordingObservationRunner(
                new ObservationRunner.ObservationResult(0, "package", ""));
        var activities = new ArrayList<String>();
        var runner = new EngineeringSessionRunner(
                new ActivityCoder(new CodingResult(0, "coded", "", Duration.ZERO)),
                observation);

        var result = runner.run(
                project,
                "Make the requested change.",
                state -> {
                },
                activities::add);

        assertEquals(EngineeringSessionRunner.SessionState.GREEN, result.state());
        assertEquals(List.of(
                "Workflow guidance: inspect existing implementation before editing.",
                "Inspecting workspace",
                "Applying change",
                "Targeted verification: running the resolved verification target.",
                "Targeted verification passed; review evidence gaps before broadening confidence."),
                activities);
        assertEquals("INSUFFICIENT_EVIDENCE", result.engineeringResult().supervision().status());
        assertTrue(result.engineeringResult().supervision().evidenceGaps().stream()
                .anyMatch(gap -> gap.contains("Semantic Copilot result unavailable")));
    }

    @Test
    void showsExplicitEngineOsTargetAsWorkspaceMismatch() throws Exception {
        createMarker("src/main/java/com/example/interview/DesktopDemoMode.java");
        var runner = new EngineeringSessionRunner(
                new StubCoder(new CodingResult(0, "coded", "", Duration.ZERO)),
                new TargetResolvingObservationRunner());

        var plan = runner.verificationPlan(
                project,
                "ENGINEOS_REQUEST_V1\nVerification Target: EngineOS");

        assertTrue(plan.mismatch());
        assertEquals(VerificationTarget.Kind.ENGINEOS, plan.target().kind());
    }

    @Test
    void showsExplicitIntAssistTargetAsWorkspaceMatch() throws Exception {
        createMarker("src/main/java/com/example/interview/DesktopDemoMode.java");
        var runner = new EngineeringSessionRunner(
                new StubCoder(new CodingResult(0, "coded", "", Duration.ZERO)),
                new TargetResolvingObservationRunner());

        var plan = runner.verificationPlan(
                project,
                "ENGINEOS_REQUEST_V1\nVerification Target: IntAssist");

        assertFalse(plan.mismatch());
        assertEquals(VerificationTarget.Kind.INTASSIST, plan.target().kind());
    }

    @Test
    void missingTargetUsesTheWorkspaceTargetWithoutGuessing() throws Exception {
        createMarker("src/main/java/com/example/interview/DesktopDemoMode.java");
        var runner = new EngineeringSessionRunner(
                new StubCoder(new CodingResult(0, "coded", "", Duration.ZERO)),
                new TargetResolvingObservationRunner());

        var plan = runner.verificationPlan(project, "ENGINEOS_REQUEST_V1\nGoal: run the demo.");

        assertFalse(plan.declaration().explicit());
        assertFalse(plan.mismatch());
        assertEquals(VerificationTarget.Kind.INTASSIST, plan.target().kind());
    }

    private static class StubCoder implements ICodingAgent {
        private final CodingResult result;
        private boolean called;

        private StubCoder(CodingResult result) {
            this.result = result;
        }

        @Override
        public AgentProvider provider() {
            return new AgentProvider("test", "stub");
        }

        @Override
        public CodingResult execute(WorkspaceContext workspace, CodingTask task) {
            called = true;
            return result;
        }
    }

    private static final class RecordingObservationRunner implements ObservationRunner {
        private final ObservationResult result;
        private boolean called;
        private boolean existingTarget;

        private RecordingObservationRunner(ObservationResult result) {
            this.result = result;
        }

        @Override
        public ObservationResult run(Path project) {
            called = true;
            return result;
        }

        @Override
        public ObservationResult run(
                WorkspaceContext workspace,
                VerificationTarget target,
                boolean existingTarget) {
            called = true;
            this.existingTarget = existingTarget;
            return result;
        }
    }

    private static final class ActivityCoder implements ICodingAgent {
        private final CodingResult result;

        private ActivityCoder(CodingResult result) {
            this.result = result;
        }

        @Override
        public AgentProvider provider() {
            return new AgentProvider("test", "activity");
        }

        @Override
        public CodingResult execute(WorkspaceContext workspace, CodingTask task) {
            return result;
        }

        @Override
        public CodingResult execute(
                WorkspaceContext workspace,
                CodingTask task,
                Consumer<String> activitySink) {
            activitySink.accept("Inspecting workspace");
            activitySink.accept("Applying change");
            return result;
        }
    }

    private void createMarker(String relativePath) throws Exception {
        Path marker = project.resolve(relativePath);
        Files.createDirectories(marker.getParent());
        Files.createFile(marker);
    }

    private void runGit(String... arguments) throws Exception {
        var command = new ArrayList<String>();
        command.add("git");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command)
                .directory(project.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) {
            throw new AssertionError("git failed: " + output);
        }
    }

    private static final class TargetResolvingObservationRunner implements ObservationRunner {
        @Override
        public ObservationResult run(Path project) {
            return new ObservationResult(0, "package", "");
        }

        @Override
        public VerificationTarget resolveTarget(
                WorkspaceContext workspace,
                Optional<VerificationTarget.Kind> requestedTarget) {
            VerificationTarget.Kind kind = requestedTarget.orElseGet(
                    () -> VerificationTarget.from(workspace).kind());
            return new VerificationTarget(kind, workspace.root(), kind.name());
        }
    }

    private static final class MutatingCoder extends StubCoder {
        private MutatingCoder(CodingResult result) {
            super(result);
        }

        @Override
        public CodingResult execute(WorkspaceContext workspace, CodingTask task) {
            try {
                Files.writeString(workspace.root().resolve("src/Main.java"),
                        "class Main {\n  // changed\n}\n");
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
            return super.execute(workspace, task);
        }
    }
}
