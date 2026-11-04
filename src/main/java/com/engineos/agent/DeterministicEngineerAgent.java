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
        return proposeTask(workspace, model, "Deterministic proposal.");
    }

    EngineeringTask proposeTask(WorkspaceContext workspace, EngineeringModel model, String source) {
        var request = EngineeringRequest.parse(model.humanIntent());
        var policy = ExecutionPolicy.forRequest(request);
        String goal = section(request, "GOAL", model.humanIntent());
        String scope = section(request, "TASK", goal);
        String candidates = EngineeringProposalContext.files(workspace).stream().limit(8)
                .collect(java.util.stream.Collectors.joining(", "));
        if (!candidates.isBlank()) {
            scope += "\nLocate relevant implementation using the discovered inventory (candidates, not a "
                    + "confirmed change list): " + candidates;
        }
        var criteria = new java.util.ArrayList<>(request.acceptanceCriteria());
        String requestedCriteria = request.sections().get("ACCEPTANCE CRITERIA");
        if (criteria.isEmpty() && requestedCriteria != null && !requestedCriteria.isBlank()) {
            criteria.add(requestedCriteria);
        }
        if (criteria.isEmpty()) {
            criteria.add("Address this requested outcome: " + goal);
        }
        criteria.add("Report supporting evidence, unverified assumptions, and any remaining gaps.");
        if (!policy.workspaceModificationAllowed()) {
            criteria.add("Leave the workspace unchanged.");
        }
        String firstLine = goal.lines().findFirst().orElse(goal);
        var task = new EngineeringTask(
                policy.phase() + ": " + firstLine.substring(0, Math.min(100, firstLine.length())),
                model.humanIntent(),
                "Address the stated goal in " + workspace.repositoryName() + " ("
                        + EngineeringProposalContext.projectType(workspace)
                        + "), starting from existing implementation and Git evidence.",
                new EngineeringTask.Proposal(goal, scope,
                        "Unrelated refactors, dependency upgrades, other workspaces, commits/pushes, "
                                + "and autonomous follow-up tasks unless explicitly requested.",
                        criteria, EngineeringProposalContext.constraints(workspace, request),
                        section(request, "VERIFICATION", EngineeringProposalContext.verification(workspace))),
                source);
        recordProposal(task, model);
        return task;
    }

    private static String section(EngineeringRequest request, String name, String fallback) {
        String value = request.sections().get(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    static void recordProposal(EngineeringTask task, EngineeringModel model) {
        model.addTask(task);
        model.addInvestigation(new Investigation("Establish the current workspace and repository baseline"));
        model.converse("Engineer Agent", "Proposed one bounded task for human review: "
                + task.title() + "\n" + task.proposalText());
    }

    @Override
    public String review(Evidence evidence, EngineeringModel model) {
        var review = "Engineer review: " + evidence.summary() + " Evidence is recorded for human inspection.";
        model.converse("Engineer Agent", review);
        return review;
    }
}
