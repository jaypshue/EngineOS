package com.engineos;

import com.engineos.agent.*;
import com.engineos.application.EngineOsWorkflow;
import com.engineos.domain.EngineeringModel;
import com.engineos.infrastructure.RepositoryDiscovery;

import java.nio.file.Path;
import java.util.List;

public final class EngineOsApplication {
    private EngineOsApplication() {
    }

    public static void main(String[] args) {
        var root = Path.of(args.length > 0 ? args[0] : ".");
        var intent = args.length > 1 ? args[1] : "Understand the current workspace";
        var workflow = new EngineOsWorkflow(
                new RepositoryDiscovery(),
                new DeterministicEngineerAgent(new AgentProvider("local", "engineer-dev")),
                new LocalCoderAgent(new AgentProvider("local", "coder-dev")));
        var workspace = workflow.loadWorkspace(root);
        var model = new EngineeringModel(intent);
        var task = workflow.receiveIntent(workspace, intent, model);
        var result = workflow.approveAndExecute(workspace, task, model, List.of("git", "status", "--short", "--branch"));
        System.out.println("Workspace: " + workspace.root());
        System.out.println("Engineer task: " + task.title());
        System.out.println("Coder result: " + result.summary());
        System.out.println("Evidence: " + model.evidence().getLast().detail());
    }
}
