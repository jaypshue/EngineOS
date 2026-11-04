package com.engineos.application;

import com.engineos.agent.AgentProvider;
import com.engineos.domain.CodingResult;
import com.engineos.domain.EngineeringTask;
import com.engineos.domain.EngineeringResult;
import com.engineos.history.EngineeringHistoryRecord;
import com.engineos.infrastructure.git.GitCommitResult;
import com.engineos.infrastructure.git.GitSnapshot;
import com.engineos.observation.ObservationRunner;
import com.engineos.observation.VerificationTarget;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Application boundary for EngineOS engineering execution and control.
 *
 * <p>Clients depend on this boundary rather than constructing or coordinating
 * coding-agent, observation, and history components themselves.</p>
 */
public interface EngineOsService {
    ServiceHealth health();

    AgentProvider codingAgentProvider();

    List<EngineeringHistoryRecord> historyRecords();

    ServiceVerificationPlan verificationPlan(Path project, String prompt);

    default GitSnapshot sourceControl(Path project) {
        return GitSnapshot.unavailable(project);
    }

    TaskProposal propose(
            String requestId,
            Path project,
            String prompt,
            String selectedModel,
            GitSnapshot sourceControlBaseline,
            Consumer<ExecutionState> stateSink,
            Consumer<String> activitySink);

    ExecutionResult approve(
            String requestId,
            Consumer<ExecutionState> stateSink,
            Consumer<String> activitySink);

    ExecutionResult reject(String requestId);

    /** Requests cancellation of the currently owned execution, if any. */
    default boolean cancelActive() {
        return false;
    }

    default boolean cancelRequest(String requestId) {
        return cancelActive();
    }

    boolean showActiveVerificationTarget();

    default GitCommitResult commitWorkspace(Path project, String runId, String message) {
        return new GitCommitResult(false, "", "", "Source-control commit is unavailable.");
    }

    record ServiceHealth(boolean ready, String service) {
    }

    enum ExecutionState {
        STARTING,
        WAITING_FOR_APPROVAL,
        APPROVED,
        CODING_AGENT_WORKING,
        RUNNING_OBSERVATION,
        GREEN,
        FAILED,
        CANCELLED,
        REJECTED
    }

    record TaskProposal(
            String requestId,
            String taskId,
            String title,
            String goal,
            String scope,
            String constraints,
            String rationale,
            String allowedActions,
            EngineeringTask.Status status,
            String outOfScope,
            List<String> acceptanceCriteria,
            String verificationApproach,
            String proposalSource,
            boolean nonModifying) {
        public TaskProposal {
            acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
        }

        public TaskProposal(String requestId, String taskId, String title, String goal, String scope,
                            String constraints, String rationale, String allowedActions,
                            EngineeringTask.Status status) {
            this(requestId, taskId, title, goal, scope, constraints, rationale, allowedActions, status,
                    "", List.of(), "", "", false);
        }

        public TaskProposal(String requestId, String taskId, String title, String goal, String scope,
                            String constraints, String rationale, String allowedActions,
                            EngineeringTask.Status status, String outOfScope,
                            List<String> acceptanceCriteria, String verificationApproach,
                            String proposalSource) {
            this(requestId, taskId, title, goal, scope, constraints, rationale, allowedActions, status,
                    outOfScope, acceptanceCriteria, verificationApproach, proposalSource, false);
        }
    }

    record ServiceVerificationPlan(
            Path workspaceRoot,
            VerificationTarget.Kind workspaceKind,
            VerificationTarget.Kind targetKind,
            boolean explicitTarget,
            boolean mismatch,
            String error) {
        public boolean resolved() {
            return error == null || error.isBlank();
        }
    }

    record ExecutionResult(
            ExecutionState state,
            String detail,
            CodingResult coding,
            ObservationRunner.ObservationResult observation,
            List<String> activity,
            EngineeringResult engineeringResult) {
        public ExecutionResult {
            activity = activity == null ? List.of() : List.copyOf(activity);
        }
    }
}
