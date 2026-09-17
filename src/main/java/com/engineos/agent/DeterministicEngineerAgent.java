package com.engineos.agent;

import com.engineos.domain.*;

public final class DeterministicEngineerAgent implements EngineerAgent {
    private final AgentProvider provider;

    public DeterministicEngineerAgent(AgentProvider provider) {
        this.provider = provider;
    }

    @Override
    public AgentProvider provider() {
        return provider;
    }

    @Override
    public EngineeringTask proposeTask(WorkspaceContext workspace, EngineeringModel model) {
        var task = new EngineeringTask(
                "Inspect the loaded workspace",
                model.humanIntent(),
                "Start with observable repository and Git evidence before proposing implementation.");
        model.addTask(task);
        model.addInvestigation(new Investigation("Establish the current workspace and repository baseline"));
        model.converse("Engineer Agent", "Proposed one bounded task for human review: " + task.title());
        return task;
    }

    @Override
    public String review(Evidence evidence, EngineeringModel model) {
        var review = "Engineer review: " + evidence.summary() + " Evidence is recorded for human inspection.";
        model.converse("Engineer Agent", review);
        return review;
    }
}
