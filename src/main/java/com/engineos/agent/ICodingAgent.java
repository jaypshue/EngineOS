package com.engineos.agent;

import com.engineos.domain.CodingResult;
import com.engineos.domain.CodingTask;
import com.engineos.domain.ExecutionPolicy;
import com.engineos.domain.WorkspaceContext;

import java.util.function.Consumer;

/**
 * EngineOS boundary for a provider that performs an engineering request.
 */
public interface ICodingAgent {
    AgentProvider provider();

    default boolean cancel() {
        return false;
    }

    CodingResult execute(WorkspaceContext workspace, CodingTask task);

    default CodingResult execute(
            WorkspaceContext workspace,
            CodingTask task,
            Consumer<String> activitySink) {
        return execute(workspace, task);
    }

    default CodingResult execute(
            WorkspaceContext workspace,
            CodingTask task,
            String model,
            Consumer<String> activitySink) {
        return execute(workspace, task, activitySink);
    }

    default CodingResult execute(
            WorkspaceContext workspace,
            CodingTask task,
            String model,
            ExecutionPolicy policy,
            Consumer<String> activitySink) {
        return execute(workspace, task, model, activitySink);
    }
}
