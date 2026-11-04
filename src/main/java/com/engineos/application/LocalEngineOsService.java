package com.engineos.application;

import com.engineos.agent.AgentProvider;
import com.engineos.history.EngineeringHistoryRecord;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import com.engineos.infrastructure.git.GitSnapshot;

/**
 * In-process EngineOS service implementation used by the desktop client.
 *
 * <p>The execution pipeline remains in {@link EngineeringSessionRunner}; this
 * adapter translates its implementation records into the stable service
 * contract.</p>
 */
public final class LocalEngineOsService implements EngineOsService {
    private final EngineeringSessionRunner runner;

    public LocalEngineOsService(EngineeringSessionRunner runner) {
        this.runner = runner;
    }

    @Override
    public ServiceHealth health() {
        return new ServiceHealth(true, "EngineOS Service");
    }

    @Override
    public AgentProvider codingAgentProvider() {
        return runner.codingAgentProvider();
    }

    @Override
    public GitSnapshot sourceControl(Path project) {
        return runner.sourceControl(project);
    }

    @Override
    public List<EngineeringHistoryRecord> historyRecords() {
        return runner.history().list();
    }

    @Override
    public ServiceVerificationPlan verificationPlan(Path project, String prompt) {
        var plan = runner.verificationPlan(project, prompt);
        return new ServiceVerificationPlan(
                plan.workspace() == null ? null : plan.workspace().root(),
                plan.workspace() == null ? null
                        : com.engineos.observation.VerificationTarget.from(plan.workspace()).kind(),
                plan.target() == null ? null : plan.target().kind(),
                plan.declaration().explicit(),
                plan.mismatch(),
                plan.error());
    }

    @Override
    public TaskProposal propose(
            String requestId,
            Path project,
            String prompt,
            String selectedModel,
            GitSnapshot sourceControlBaseline,
            Consumer<ExecutionState> stateSink,
            Consumer<String> activitySink) {
        return runner.propose(requestId, project, prompt, selectedModel, sourceControlBaseline,
                    state -> stateSink.accept(toExecutionState(state)), activitySink);
    }

    @Override
    public ExecutionResult approve(
            String requestId,
            Consumer<ExecutionState> stateSink,
            Consumer<String> activitySink) {
        var result = runner.approve(requestId,
                state -> stateSink.accept(toExecutionState(state)), activitySink);
        return new ExecutionResult(
                ExecutionState.valueOf(result.state().name()),
                result.detail(),
                result.coding(),
                result.observation(),
                result.activity(),
                result.engineeringResult());
    }

    @Override
    public ExecutionResult reject(String requestId) {
        var result = runner.reject(requestId, state -> { }, activity -> { });
        return executionResult(result);
    }

    @Override
    public boolean cancelRequest(String requestId) {
        var result = runner.cancelPending(requestId);
        if (result != null) {
            return true;
        }
        return runner.cancelActive();
    }

    private static ExecutionResult executionResult(EngineeringSessionRunner.SessionResult result) {
        return new ExecutionResult(
                ExecutionState.valueOf(result.state().name()),
                result.detail(),
                result.coding(),
                result.observation(),
                result.activity(),
                result.engineeringResult());
    }

    private static ExecutionState toExecutionState(EngineeringSessionRunner.SessionState state) {
        return state == EngineeringSessionRunner.SessionState.AWAITING_APPROVAL
                ? ExecutionState.WAITING_FOR_APPROVAL
                : ExecutionState.valueOf(state.name());
    }

    @Override
    public boolean cancelActive() {
        return runner.cancelActive();
    }

    @Override
    public boolean showActiveVerificationTarget() {
        return runner.showActiveVerificationTarget();
    }

    @Override
    public com.engineos.infrastructure.git.GitCommitResult commitWorkspace(
            Path project, String runId, String message) {
        return runner.commit(project, runId, message);
    }
}
