package com.engineos.application;

import com.engineos.agent.AgentProvider;
import com.engineos.domain.CodingResult;
import com.engineos.domain.EngineeringResult;
import com.engineos.history.EngineeringHistoryRecord;
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

    ExecutionResult submit(
            Path project,
            String prompt,
            String selectedModel,
            Consumer<ExecutionState> stateSink,
            Consumer<String> activitySink);

    /** Requests cancellation of the currently owned execution, if any. */
    default boolean cancelActive() {
        return false;
    }

    boolean showActiveVerificationTarget();

    record ServiceHealth(boolean ready, String service) {
    }

    enum ExecutionState {
        STARTING,
        CODING_AGENT_WORKING,
        RUNNING_OBSERVATION,
        GREEN,
        FAILED,
        CANCELLED
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
