package com.engineos.application;

import com.engineos.agent.CoderAgent;
import com.engineos.agent.EngineerAgent;
import com.engineos.domain.*;
import com.engineos.infrastructure.RepositoryDiscovery;

import java.nio.file.Path;

public final class EngineOsWorkflow {
    private final RepositoryDiscovery discovery;
    private final EngineerAgent engineer;
    private final CoderAgent coder;

    public EngineOsWorkflow(RepositoryDiscovery discovery, EngineerAgent engineer, CoderAgent coder) {
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
        task.approve();
        var result = coder.execute(workspace, new CodingTask(task, approvedCommand));
        task.complete();
        var evidence = new Evidence(null, Evidence.EvidenceType.EXECUTION_RESULT,
                result.summary(), result.standardOutput() + result.standardError(), null);
        model.recordEvidence(evidence);
        model.investigations().getLast().record(evidence);
        engineer.review(evidence, model);
        return result;
    }
}
