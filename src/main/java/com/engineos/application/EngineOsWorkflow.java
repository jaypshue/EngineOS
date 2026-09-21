package com.engineos.application;

import com.engineos.agent.EngineerAgent;
import com.engineos.agent.ICodingAgent;
import com.engineos.domain.*;
import com.engineos.infrastructure.RepositoryDiscovery;

import java.nio.file.Path;
import java.util.function.Consumer;

public final class EngineOsWorkflow {
    private final RepositoryDiscovery discovery;
    private final EngineerAgent engineer;
    private final ICodingAgent coder;

    public EngineOsWorkflow(RepositoryDiscovery discovery, EngineerAgent engineer, ICodingAgent coder) {
        this.discovery = discovery;
        this.engineer = engineer;
        this.coder = coder;
    }

    public WorkspaceContext loadWorkspace(Path root) {
        return discovery.load(root);
    }

    public EngineeringTask receiveIntent(WorkspaceContext workspace, String humanIntent, EngineeringModel model) {
        return engineer.proposeTask(workspace, model);
    }

    public CodingResult approveAndExecute(
            WorkspaceContext workspace,
            EngineeringTask task,
            EngineeringModel model,
            java.util.List<String> approvedCommand) {
        return approveAndExecute(workspace, task, model, approvedCommand, activity -> {
        });
    }

    public CodingResult approveAndExecute(
            WorkspaceContext workspace,
            EngineeringTask task,
            EngineeringModel model,
            java.util.List<String> approvedCommand,
            Consumer<String> activitySink) {
        return approveAndExecute(workspace, task, model, approvedCommand, null, activitySink);
    }

    public CodingResult approveAndExecute(
            WorkspaceContext workspace,
            EngineeringTask task,
            EngineeringModel model,
            java.util.List<String> approvedCommand,
            String selectedModel,
            Consumer<String> activitySink) {
        return approveAndExecute(workspace, task, model, approvedCommand, selectedModel,
                null, activitySink);
    }

    public CodingResult approveAndExecute(
            WorkspaceContext workspace,
            EngineeringTask task,
            EngineeringModel model,
            java.util.List<String> approvedCommand,
            String selectedModel,
            ExecutionPolicy policy,
            Consumer<String> activitySink) {
        task.approve();
        var codingTask = new CodingTask(task, approvedCommand);
        var result = selectedModel == null
                ? policy == null
                ? coder.execute(workspace, codingTask, activitySink)
                : coder.execute(workspace, codingTask, providerModel(), policy, activitySink)
                : policy == null
                ? coder.execute(workspace, codingTask, selectedModel, activitySink)
                : coder.execute(workspace, codingTask, selectedModel, policy, activitySink);
        task.complete();
        var evidence = new Evidence(null, Evidence.EvidenceType.EXECUTION_RESULT,
                result.summary(), result.report() + "\n" + result.standardOutput() + result.standardError(), null);
        model.recordEvidence(evidence);
        model.investigations().getLast().record(evidence);
        engineer.review(evidence, model);
        return result;
    }

    private String providerModel() {
        return coder.provider().modelId();
    }
}
