package com.engineos.application;

import com.engineos.agent.AgentProvider;
import com.engineos.history.EngineeringHistoryRecord;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

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
    public ExecutionResult submit(
            Path project,
            String prompt,
            String selectedModel,
            Consumer<ExecutionState> stateSink,
            Consumer<String> activitySink) {
        var result = runner.run(project, prompt, selectedModel,
                state -> stateSink.accept(ExecutionState.valueOf(state.name())),
                activitySink);
        return new ExecutionResult(
                ExecutionState.valueOf(result.state().name()),
                result.detail(),
                result.coding(),
                result.observation(),
                result.activity(),
                result.engineeringResult());
    }

    @Override
    public boolean cancelActive() {
        return runner.cancelActive();
    }

    @Override
    public boolean showActiveVerificationTarget() {
        return runner.showActiveVerificationTarget();
    }
}
