package com.engineos.application;

import com.engineos.agent.AgentProvider;
import com.engineos.agent.DeterministicEngineerAgent;
import com.engineos.agent.ICodingAgent;
import com.engineos.agent.EngineerAgent;
import com.engineos.domain.CodingResult;
import com.engineos.domain.CopilotResultDetails;
import com.engineos.domain.EngineeringModel;
import com.engineos.domain.EngineeringRequest;
import com.engineos.domain.ExecutionControl;
import com.engineos.domain.ExecutionPhase;
import com.engineos.domain.ExecutionPolicy;
import com.engineos.infrastructure.RepositoryDiscovery;
import com.engineos.infrastructure.WorkspaceChangeSummary;
import com.engineos.infrastructure.WorkspaceSnapshot;
import com.engineos.history.EngineeringHistoryRecord;
import com.engineos.history.EngineeringHistoryRepository;
import com.engineos.history.NoOpEngineeringHistoryRepository;
import com.engineos.history.SourceControlHistory;
import com.engineos.infrastructure.git.GitRepository;
import com.engineos.infrastructure.git.GitCommitResult;
import com.engineos.infrastructure.git.GitSnapshot;
import com.engineos.infrastructure.git.ProcessGitRepository;
import com.engineos.observation.ObservationRunner;
import com.engineos.observation.ObservationEvidencePackageWriter;
import com.engineos.observation.VerificationTarget;
import com.engineos.observation.VerificationTargetDeclaration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;

public final class EngineeringSessionRunner {
    private final RepositoryDiscovery discovery;
    private final EngineOsWorkflow workflow;
    private final ICodingAgent coder;
    private final ObservationRunner observationRunner;
    private final EngineeringHistoryRepository history;
    private final GitRepository git;
    private final AtomicBoolean cancellationRequested = new AtomicBoolean();
    private final ConcurrentHashMap<String, PreparedSession> pendingSessions = new ConcurrentHashMap<>();

    public EngineeringSessionRunner(ICodingAgent coder, ObservationRunner observationRunner) {
        this(new RepositoryDiscovery(), coder, observationRunner,
                new NoOpEngineeringHistoryRepository(), new ProcessGitRepository());
    }

    EngineeringSessionRunner(
            RepositoryDiscovery discovery,
            ICodingAgent coder,
            ObservationRunner observationRunner) {
        this(discovery, coder, observationRunner, new NoOpEngineeringHistoryRepository(),
                new ProcessGitRepository());
    }

    public EngineeringSessionRunner(
            ICodingAgent coder,
            ObservationRunner observationRunner,
            EngineeringHistoryRepository history) {
        this(new RepositoryDiscovery(), coder, observationRunner, history, new ProcessGitRepository());
    }

    public EngineeringSessionRunner(
            EngineerAgent engineer,
            ICodingAgent coder,
            ObservationRunner observationRunner,
            EngineeringHistoryRepository history) {
        this(new RepositoryDiscovery(), engineer, coder, observationRunner, history, new ProcessGitRepository());
    }

    EngineeringSessionRunner(
            RepositoryDiscovery discovery,
            ICodingAgent coder,
            ObservationRunner observationRunner,
            EngineeringHistoryRepository history) {
        this(discovery, coder, observationRunner, history, new ProcessGitRepository());
    }

    EngineeringSessionRunner(
            RepositoryDiscovery discovery,
            ICodingAgent coder,
            ObservationRunner observationRunner,
            EngineeringHistoryRepository history,
            GitRepository git) {
        this(discovery, new DeterministicEngineerAgent(new AgentProvider("local", "engineer-dev")),
                coder, observationRunner, history, git);
    }

    private EngineeringSessionRunner(
            RepositoryDiscovery discovery,
            EngineerAgent engineer,
            ICodingAgent coder,
            ObservationRunner observationRunner,
            EngineeringHistoryRepository history,
            GitRepository git) {
        this.discovery = Objects.requireNonNull(discovery);
        this.coder = Objects.requireNonNull(coder);
        this.observationRunner = Objects.requireNonNull(observationRunner);
        this.history = Objects.requireNonNull(history);
        this.git = Objects.requireNonNull(git);
        this.workflow = new EngineOsWorkflow(
                this.discovery,
                Objects.requireNonNull(engineer),
                this.coder);
    }

    public AgentProvider codingAgentProvider() {
        return coder.provider();
    }

    public EngineeringHistoryRepository history() {
        return history;
    }

    public GitSnapshot sourceControl(Path project) {
        return git.snapshot(project);
    }

    public VerificationPlan verificationPlan(Path project, String prompt) {
            try {
            var workspace = discovery.load(project);
            var declaration = VerificationTargetDeclaration.parse(prompt);
            if (!declaration.error().isBlank()) {
                return new VerificationPlan(workspace, null, declaration, declaration.error());
            }
            var target = observationRunner.resolveTarget(workspace, declaration.requestedTarget());
            return new VerificationPlan(workspace, target, declaration, "");
        } catch (RuntimeException exception) {
            return new VerificationPlan(null, null,
                    VerificationTargetDeclaration.parse(prompt), exception.getMessage());
        }
    }

    public EngineOsService.TaskProposal propose(
            String requestId,
            Path project,
            String prompt,
            String selectedModel,
            GitSnapshot sourceControlBaseline,
            Consumer<SessionState> stateSink,
            Consumer<String> activitySink) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("Request ID is required.");
        }
        if (project == null || !Files.isDirectory(project)) {
            throw new IllegalArgumentException("Project directory does not exist: " + project);
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Engineering prompt is required.");
        }
        if (pendingSessions.containsKey(requestId)) {
            throw new IllegalStateException("An Engineering Task has already been proposed for this request.");
        }

        stateSink.accept(SessionState.STARTING);
        var workspace = discovery.load(project);
        var model = new EngineeringModel(prompt);
        var task = workflow.receiveIntent(workspace, prompt, model);
        var policy = ExecutionPolicy.forRequest(EngineeringRequest.parse(prompt));
        var declaration = VerificationTargetDeclaration.parse(prompt);
        if (!declaration.error().isBlank()) {
            throw new IllegalArgumentException(declaration.error());
        }
        var target = observationRunner.resolveTarget(workspace, declaration.requestedTarget());
        var prepared = new PreparedSession(
                requestId,
                Instant.now(),
                project.toAbsolutePath().normalize(),
                prompt,
                selectedModel,
                workspace,
                model,
                task,
                policy,
                target,
                sourceControlBaseline == null ? git.snapshot(project) : sourceControlBaseline,
                WorkspaceSnapshot.capture(project));
        addActivity(prepared, "Engineering Task proposed (PROPOSED): " + task.title()
                + "\n" + task.proposalText()
                + ". Awaiting explicit human approval.", activitySink);
        if (pendingSessions.putIfAbsent(requestId, prepared) != null) {
            throw new IllegalStateException("An Engineering Task has already been proposed for this request.");
        }
        stateSink.accept(SessionState.AWAITING_APPROVAL);
        return proposal(prepared);
    }

    public SessionResult approve(
            String requestId,
            Consumer<SessionState> stateSink,
            Consumer<String> activitySink) {
        cancellationRequested.set(false);
        PreparedSession prepared = pendingSessions.remove(requestId);
        if (prepared == null) {
            throw new IllegalStateException("No pending Engineering Task exists for request " + requestId);
        }
        prepared.task.approve();
        addActivity(prepared, "Human approved Engineering Task " + prepared.task.id()
                + " (" + prepared.task.title() + ").", activitySink);
        stateSink.accept(SessionState.APPROVED);
        prepared.executionStarted = Instant.now();
        try {
            var policy = prepared.policy;
            var verificationTarget = prepared.verificationTarget;
            addActivity(prepared, "Workflow guidance: inspect existing implementation before editing.", activitySink);
            if (policy.phase() == ExecutionPhase.VERIFICATION) {
                addActivity(prepared, "Execution phase: " + policy.phase()
                        + " (workspace modification prohibited).", activitySink);
            }
            stateSink.accept(SessionState.CODING_AGENT_WORKING);
            CodingResult coding;
            if (policy.phase() == ExecutionPhase.VERIFICATION) {
                String message = "Verification-only execution: Coding Agent skipped; "
                        + "EngineOS controls deterministic behavioral observation.";
                addActivity(prepared, message, activitySink);
                coding = new CodingResult(0, message, "", java.time.Duration.ZERO,
                        prepared.selectedModel, com.engineos.agent.AiUsage.unavailable());
                prepared.task.complete();
            } else {
                coding = workflow.executeApproved(
                        prepared.workspace, prepared.task, prepared.model, List.of(), prepared.selectedModel,
                        policy, activity -> addActivity(prepared, activity, activitySink));
            }
            var finalSnapshot = WorkspaceSnapshot.capture(prepared.project);
            var sourceControlAfter = git.snapshot(prepared.project);
            var changedFiles = finalSnapshot.changedSince(prepared.baseline);
            var finalChanges = WorkspaceChangeSummary.capture(prepared.project);
            boolean boundaryViolation = !policy.workspaceModificationAllowed() && !changedFiles.isEmpty();
            String termination = boundaryViolation ? "BOUNDARY_VIOLATION"
                    : coding.exitCode() == 124 ? "TIMED_OUT"
                    : coding.exitCode() == 125 ? "BOUNDARY_VIOLATION"
                    : coding.exitCode() == 130 ? "CANCELLED"
                    : coding.succeeded() ? "COMPLETED" : "FAILED";
            var control = new ExecutionControl(
                    policy.phase(), policy, prepared.executionStarted, Instant.now(), termination,
                    boundaryViolation || coding.exitCode() == 125,
                    finalChanges == null ? changedFiles : finalChanges.modifiedFiles(), 0,
                    List.of(policy.phase()));
            if (boundaryViolation) {
                addActivity(prepared, "Execution boundary violation: prohibited workspace changes detected.",
                        activitySink);
                return failure(prepared.runId, prepared.startedAt, prepared.project, prepared.prompt,
                        prepared.selectedModel, "Verification changed protected workspace files: " + changedFiles,
                        coding, null, prepared.activities, null, stateSink, control, finalChanges,
                        prepared.sourceControlBefore, sourceControlAfter);
            }
            if (coding.exitCode() == 130 || cancellationRequested.get()) {
                addActivity(prepared, "Coding agent execution cancelled.", activitySink);
                return cancelled(prepared.runId, prepared.startedAt, prepared.project, prepared.prompt,
                        prepared.selectedModel, coding, prepared.activities, stateSink, control, finalChanges,
                        prepared.sourceControlBefore, sourceControlAfter);
            }
            if (!coding.succeeded()) {
                return failure(prepared.runId, prepared.startedAt, prepared.project, prepared.prompt,
                        prepared.selectedModel, coding.summary() + " " + coding.standardError(),
                        coding, null, prepared.activities, null, stateSink, control, finalChanges,
                        prepared.sourceControlBefore, sourceControlAfter);
            }
            addActivity(prepared, verificationTarget.kind() == VerificationTarget.Kind.REPOSITORY
                    ? "Repository investigation target: capturing the investigation result."
                    : "Targeted verification: running the resolved verification target.", activitySink);
            stateSink.accept(SessionState.RUNNING_OBSERVATION);
            var observation = observationRunner.run(
                    prepared.workspace,
                    verificationTarget,
                    policy.phase() == ExecutionPhase.VERIFICATION
                            && verificationTarget.kind() == VerificationTarget.Kind.ENGINEOS);
            if (!observation.succeeded()) {
                addActivity(prepared,
                        "Targeted verification failed; correction and re-verification remain required.",
                        activitySink);
                return failure(prepared.runId, prepared.startedAt, prepared.project, prepared.prompt,
                        prepared.selectedModel, "Observation failed: " + observation.error(), coding, observation,
                        prepared.activities, verificationTarget, stateSink, control, finalChanges,
                        prepared.sourceControlBefore, sourceControlAfter);
            }
            if (policy.phase() == ExecutionPhase.VERIFICATION) {
                var observationChanges = WorkspaceSnapshot.capture(prepared.project)
                        .changedSince(prepared.baseline);
                if (!observationChanges.isEmpty()) {
                    addActivity(prepared, "Verification boundary violation: protected workspace files changed.",
                            activitySink);
                    return failure(prepared.runId, prepared.startedAt, prepared.project, prepared.prompt,
                            prepared.selectedModel, "Verification changed protected workspace files during "
                                    + "observation: " + observationChanges,
                            coding, observation, prepared.activities, verificationTarget, stateSink, control,
                            WorkspaceChangeSummary.capture(prepared.project),
                            prepared.sourceControlBefore, git.snapshot(prepared.project));
                }
            }
            sourceControlAfter = git.snapshot(prepared.project);
            addActivity(prepared, verificationTarget.kind() == VerificationTarget.Kind.REPOSITORY
                    ? "Repository investigation result captured; review evidence gaps before broadening confidence."
                    : "Targeted verification passed; review evidence gaps before broadening confidence.",
                    activitySink);
            stateSink.accept(SessionState.GREEN);
            return complete(prepared.runId, prepared.startedAt, prepared.project, prepared.prompt,
                    prepared.selectedModel,
                    new SessionResult(SessionState.GREEN, "GREEN - Observation ready", coding, observation,
                            prepared.activities),
                    verificationTarget, control, finalChanges, prepared.sourceControlBefore, sourceControlAfter);
        } catch (RuntimeException exception) {
            return failure(prepared.runId, prepared.startedAt, prepared.project, prepared.prompt,
                    prepared.selectedModel, "Session failed: " + exception.getMessage(), null, null,
                    prepared.activities, stateSink);
        }
    }

    public SessionResult reject(String requestId, Consumer<SessionState> stateSink, Consumer<String> activitySink) {
        PreparedSession prepared = pendingSessions.remove(requestId);
        if (prepared == null) {
            throw new IllegalStateException("No pending Engineering Task exists for request " + requestId);
        }
        prepared.task.reject();
        addActivity(prepared, "Human rejected Engineering Task " + prepared.task.id()
                + " (" + prepared.task.title() + "); Coding Agent was not invoked.", activitySink);
        stateSink.accept(SessionState.REJECTED);
        return finishWithoutExecution(prepared, SessionState.REJECTED,
                "REJECTED - Human declined the proposed task.", "REJECTED");
    }

    public SessionResult cancelPending(String requestId) {
        PreparedSession prepared = pendingSessions.remove(requestId);
        if (prepared == null) {
            return null;
        }
        prepared.activities.add("Request cancelled before human approval; Coding Agent was not invoked.");
        return finishWithoutExecution(prepared, SessionState.CANCELLED,
                "CANCELLED - Request cancelled before approval.", "CANCELLED");
    }

    private SessionResult finishWithoutExecution(
            PreparedSession prepared,
            SessionState state,
            String detail,
            String termination) {
        var changes = WorkspaceChangeSummary.capture(prepared.project);
        var control = new ExecutionControl(
                prepared.policy.phase(), prepared.policy, prepared.startedAt, Instant.now(), termination,
                false, changes == null ? List.of() : changes.modifiedFiles(), 0,
                List.of(prepared.policy.phase()));
        return complete(prepared.runId, prepared.startedAt, prepared.project, prepared.prompt,
                prepared.selectedModel,
                new SessionResult(state, detail, null, null, prepared.activities),
                prepared.verificationTarget, control, changes,
                prepared.sourceControlBefore, git.snapshot(prepared.project));
    }

    private static EngineOsService.TaskProposal proposal(PreparedSession prepared) {
        var details = prepared.task.proposal();
        String allowedActions = switch (prepared.policy.phase()) {
            case VERIFICATION -> "After approval, EngineOS runs bounded verification only; the Coding Agent is skipped "
                    + "and workspace modifications are prohibited.";
            case INVESTIGATION -> "After approval, the Coding Agent may inspect the selected workspace but may not "
                    + "modify it; EngineOS records the resulting evidence.";
            case IMPLEMENTATION -> "After approval, GitHub Copilot may use its configured tools and modify files in "
                    + "the selected workspace. EngineOS records changes and runs the resolved verification target.";
        };
        return new EngineOsService.TaskProposal(
                prepared.runId, prepared.task.id().toString(), prepared.task.title(), details.goal(), details.scope(),
                details.constraints(), prepared.task.rationale(), allowedActions, prepared.task.status(),
                details.outOfScope(), details.acceptanceCriteria(), details.verificationApproach(),
                prepared.task.proposalSource());
    }

    private static void addActivity(
            PreparedSession prepared, String activity, Consumer<String> activitySink) {
        prepared.activities.add(activity);
        activitySink.accept(activity);
    }

    public boolean showActiveVerificationTarget() {
        return observationRunner.showActiveTarget();
    }

    public boolean cancelActive() {
        if (!cancellationRequested.compareAndSet(false, true)) {
            return false;
        }
        coder.cancel();
        return true;
    }

    public GitCommitResult commit(Path project, String runId, String message) {
        var result = git.commit(project, message);
        if (result.succeeded() && runId != null && !runId.isBlank()) {
            history.get(runId).ifPresent(record -> {
                var sourceControl = record.sourceControl();
                if (sourceControl != null) {
                    history.save(new EngineeringHistoryRecord(
                            record.runId(), record.startedAt(), record.completedAt(), record.duration(),
                            record.engineeringRequest(), record.requestSummary(), record.codingAgent(),
                            record.provider(), record.requestedModel(), record.actualModel(), record.workspace(),
                            record.verificationTarget(), record.finalStatus(), record.codingResultStatus(),
                            record.exitCode(), record.sessionId(), record.aiUsage(), record.apiDurationMs(),
                            record.sessionDurationMs(), record.linesAdded(), record.linesRemoved(),
                            record.filesModified(), record.modifiedFiles(), record.semanticResultAvailable(),
                            record.semanticResultComplete(), record.engineeringResult(), record.activity(),
                            record.observationStatus(), record.observationAvailable(), record.observationLocation(),
                            record.verificationSummary(), record.verificationCommand(),
                            record.verificationExitCode(), record.verificationDuration(), record.verificationOutput(),
                            record.verificationError(), sourceControl.withCommit(result.commitHash(), message)));
                }
            });
        }
        return result;
    }

    public record VerificationPlan(
            com.engineos.domain.WorkspaceContext workspace,
            VerificationTarget target,
            VerificationTargetDeclaration declaration,
            String error) {
        public boolean resolved() {
            return error == null || error.isBlank();
        }

        public boolean mismatch() {
            return resolved()
                    && workspace != null
                    && target != null
                    && VerificationTarget.from(workspace).kind() != target.kind();
        }
    }

    private SessionResult failure(
            String runId,
            Instant startedAt,
            Path project,
            String prompt,
            String selectedModel,
            String detail,
            CodingResult coding,
            ObservationRunner.ObservationResult observation,
            List<String> activities,
            Consumer<SessionState> stateSink) {
        return failure(runId, startedAt, project, prompt, selectedModel, detail,
                coding, observation, activities, null, stateSink, null,
                WorkspaceChangeSummary.capture(project), null, git.snapshot(project));
    }

    private SessionResult cancelled(
            String runId, Instant startedAt, Path project, String prompt, String selectedModel,
            CodingResult coding, List<String> activities, Consumer<SessionState> stateSink,
            ExecutionControl control, WorkspaceChangeSummary finalChanges,
            GitSnapshot sourceControlBefore, GitSnapshot sourceControlAfter) {
        stateSink.accept(SessionState.CANCELLED);
        return complete(runId, startedAt, project, prompt, selectedModel,
                new SessionResult(SessionState.CANCELLED, "CANCELLED - Implementation not complete",
                        coding, null, activities), null, control, finalChanges,
                sourceControlBefore, sourceControlAfter);
    }

    private SessionResult failure(
            String runId,
            Instant startedAt,
            Path project,
            String prompt,
            String selectedModel,
            String detail,
            CodingResult coding,
            ObservationRunner.ObservationResult observation,
            List<String> activities,
            VerificationTarget verificationTarget,
            Consumer<SessionState> stateSink,
            ExecutionControl control,
            WorkspaceChangeSummary finalChanges,
            GitSnapshot sourceControlBefore,
            GitSnapshot sourceControlAfter) {
        stateSink.accept(SessionState.FAILED);
        return complete(runId, startedAt, project, prompt, selectedModel,
                new SessionResult(SessionState.FAILED, "FAILED - " + detail, coding, observation, activities),
                verificationTarget, control, finalChanges, sourceControlBefore, sourceControlAfter);
    }

    private SessionResult complete(
            String runId,
            Instant startedAt,
            Path project,
            String prompt,
            String selectedModel,
            SessionResult result,
            VerificationTarget verificationTarget,
            ExecutionControl control,
            WorkspaceChangeSummary finalChanges,
            GitSnapshot sourceControlBefore,
            GitSnapshot sourceControlAfter) {
        CodingResult coding = result.coding();
        var observation = result.observation();
        var details = coding == null ? null : coding.details();
        var persistedDetails = finalDetails(finalChanges, details);
        var record = EngineeringHistoryRecord.from(
                runId, startedAt, Instant.now(), prompt,
                requestSummary(prompt), coder.provider().displayName(), coder.provider().providerId(),
                coding == null || coding.requestedModel() == null ? selectedModel : coding.requestedModel(),
                project == null ? null : project.toAbsolutePath().normalize().toString(),
                verificationTarget == null ? null : verificationTarget.kind().name(),
                result.state().name(),
                coding == null ? "UNAVAILABLE" : coding.summary(),
                coding == null ? null : coding.exitCode(),
                coding == null ? null : coding.sessionId(),
                coding == null ? null : coding.usage(),
                persistedDetails,
                coding == null ? result.detail() : engineeringReport(coding, persistedDetails, result.detail()),
                observation == null ? "NOT_RUN" : observation.succeeded() ? "SUCCEEDED" : "FAILED",
                observation != null && observation.succeeded(),
                null,
                verificationTarget == null ? null : verificationTarget.kind().name(),
                observation,
                result.activity(),
                SourceControlHistory.from(sourceControlBefore, sourceControlAfter));
        history.save(record);
        var completed = result.withEngineeringResult(toEngineeringResult(
                result, prompt, record, coder.provider().displayName(), control));
        ObservationEvidencePackageWriter.writeIfAvailable(
                observation, prompt, completed.engineeringResult(),
                coding == null ? null : coding.standardOutput(),
                observationRunner.observationsRoot());
        return completed;
    }

    private static String engineeringReport(
            CodingResult coding,
            CopilotResultDetails details,
            String detail) {
        String report = coding.report(details) + "\n" + detail;
        if (coding.standardOutput() == null || coding.standardOutput().isBlank()) {
            return report;
        }
        return report + "\n\nCODING AGENT FINDINGS\n" + coding.standardOutput().trim();
    }

    private static com.engineos.domain.EngineeringResult toEngineeringResult(
            SessionResult result,
            String prompt,
            EngineeringHistoryRecord record,
            String codingAgent,
            ExecutionControl control) {
        CodingResult coding = result.coding();
        var observation = result.observation();
        var details = coding == null ? null : coding.details();
        var changes = new com.engineos.domain.EngineeringResult.Changes(
                record.linesAdded(),
                record.linesRemoved(),
                record.filesModified(),
                record.modifiedFiles());
        var execution = new com.engineos.domain.EngineeringResult.Execution(
                codingAgent,
                coding == null ? null : coding.requestedModel(),
                details != null && details.actualModel() != null
                        ? details.actualModel()
                        : coding == null || coding.usage() == null ? null : coding.usage().model(),
                coding == null ? null : coding.sessionId(),
                coding == null ? null : coding.duration(),
                coding == null ? null : coding.usage());
        var verification = new com.engineos.domain.EngineeringResult.Verification(
                observation == null ? "NOT_RUN" : observation.succeeded() ? "SUCCEEDED" : "FAILED",
                observation == null ? null : observation.output(),
                observation == null ? null : observation.error(),
                observation != null && observation.succeeded());
        var observationReference = record.observationLocation();
        var observationData = new com.engineos.domain.EngineeringResult.Observation(
                verification.status(), verification.output(), verification.error(),
                observation != null, observationReference);
        var uncertainties = new ArrayList<String>();
        if (result.state() == SessionState.REJECTED) {
            uncertainties.add("Coding was not run because the human rejected the proposed task.");
        } else if (result.state() == SessionState.CANCELLED && coding == null) {
            uncertainties.add("Coding was not run because the request was cancelled before approval.");
        } else if (coding == null || !coding.succeeded()) {
            uncertainties.add("Implementation did not complete successfully.");
        }
        if (coding == null || !coding.semanticResultAvailable()) {
            uncertainties.add("Semantic Copilot result unavailable.");
        } else if (!coding.semanticResultComplete()) {
            uncertainties.add("Semantic Copilot result incomplete.");
        }
        if (observation == null) {
            uncertainties.add("Verification did not run.");
        } else if (!observation.succeeded()) {
            uncertainties.add("Targeted verification failed; behavior remains unverified.");
        }
        if (coding != null && coding.sessionId() == null) {
            uncertainties.add("Copilot session unavailable.");
        }
        boolean cancelled = result.state() == SessionState.CANCELLED;
        var assessment = com.engineos.history.EngineeringAssessment.from(record);
        String supervisionStatus = result.state() == SessionState.REJECTED ? "REJECTED"
                : cancelled ? "CANCELLED"
                : "FAILED".equals(assessment.verificationStatus()) ? "FAILED"
                : "SUFFICIENT".equals(assessment.evidenceStatus()) ? "VERIFIED"
                : "INSUFFICIENT_EVIDENCE";
        String implementationStatus = result.state() == SessionState.REJECTED ? "REJECTED"
                : cancelled ? "CANCELLED"
                : control != null && control.phase() == ExecutionPhase.INVESTIGATION
                ? coding != null && coding.succeeded() ? "INVESTIGATED" : "NOT_INVESTIGATED"
                : coding != null && coding.succeeded() ? "IMPLEMENTED" : "NOT_IMPLEMENTED";
        return new com.engineos.domain.EngineeringResult(
                prompt,
                execution,
                result.activity(),
                changes,
                verification,
                observationData,
                new com.engineos.domain.EngineeringResult.History(record.runId(), true),
                new com.engineos.domain.EngineeringResult.Supervision(
                        supervisionStatus,
                        "VERIFIED".equals(supervisionStatus),
                        uncertainties,
                        implementationStatus),
                control,
                assessment);
    }

    private static CopilotResultDetails finalDetails(
            WorkspaceChangeSummary finalChanges,
            CopilotResultDetails semanticDetails) {
        if (finalChanges == null) {
            return semanticDetails;
        }
        return new CopilotResultDetails(
                semanticDetails != null && semanticDetails.available(),
                semanticDetails == null ? null : semanticDetails.actualModel(),
                semanticDetails == null ? null : semanticDetails.totalApiDurationMs(),
                semanticDetails == null ? null : semanticDetails.sessionDurationMs(),
                finalChanges.linesAdded(),
                finalChanges.linesRemoved(),
                finalChanges.filesModified(),
                finalChanges.modifiedFiles());
    }

    private static String requestSummary(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "";
        }
        return prompt.replaceAll("\\s+", " ").trim().length() > 120
                ? prompt.replaceAll("\\s+", " ").trim().substring(0, 117) + "..."
                : prompt.replaceAll("\\s+", " ").trim();
    }

    public enum SessionState {
        STARTING,
        AWAITING_APPROVAL,
        APPROVED,
        CODING_AGENT_WORKING,
        RUNNING_OBSERVATION,
        GREEN,
        FAILED,
        CANCELLED,
        REJECTED
    }

    private static final class PreparedSession {
        private final String runId;
        private final Instant startedAt;
        private final Path project;
        private final String prompt;
        private final String selectedModel;
        private final com.engineos.domain.WorkspaceContext workspace;
        private final EngineeringModel model;
        private final com.engineos.domain.EngineeringTask task;
        private final ExecutionPolicy policy;
        private final VerificationTarget verificationTarget;
        private final GitSnapshot sourceControlBefore;
        private final WorkspaceSnapshot baseline;
        private final List<String> activities = new ArrayList<>();
        private Instant executionStarted;

        private PreparedSession(
                String runId,
                Instant startedAt,
                Path project,
                String prompt,
                String selectedModel,
                com.engineos.domain.WorkspaceContext workspace,
                EngineeringModel model,
                com.engineos.domain.EngineeringTask task,
                ExecutionPolicy policy,
                VerificationTarget verificationTarget,
                GitSnapshot sourceControlBefore,
                WorkspaceSnapshot baseline) {
            this.runId = runId;
            this.startedAt = startedAt;
            this.project = project;
            this.prompt = prompt;
            this.selectedModel = selectedModel;
            this.workspace = workspace;
            this.model = model;
            this.task = task;
            this.policy = policy;
            this.verificationTarget = verificationTarget;
            this.sourceControlBefore = sourceControlBefore;
            this.baseline = baseline;
        }
    }

    public record SessionResult(
            SessionState state,
            String detail,
            CodingResult coding,
            ObservationRunner.ObservationResult observation,
            List<String> activity,
            com.engineos.domain.EngineeringResult engineeringResult,
            ExecutionControl executionControl) {
        public SessionResult(
                SessionState state,
                String detail,
                CodingResult coding,
                ObservationRunner.ObservationResult observation,
                List<String> activity) {
            this(state, detail, coding, observation, activity, null, null);
        }

        public SessionResult(
                SessionState state,
                String detail,
                CodingResult coding,
                ObservationRunner.ObservationResult observation) {
            this(state, detail, coding, observation, List.of(), null, null);
        }

        public SessionResult withEngineeringResult(com.engineos.domain.EngineeringResult engineeringResult) {
            return new SessionResult(state, detail, coding, observation, activity,
                    engineeringResult, executionControl);
        }

        public SessionResult {
            activity = activity == null ? List.of() : List.copyOf(activity);
        }
    }
}
