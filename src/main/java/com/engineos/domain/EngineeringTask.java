package com.engineos.domain;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

public final class EngineeringTask {
    public enum Status {
        PROPOSED, APPROVED, COMPLETED, REJECTED
    }

    private final UUID id = UUID.randomUUID();
    private final String title;
    private final String intent;
    private final String rationale;
    private final Proposal proposal;
    private final String proposalSource;
    private final Instant createdAt = Instant.now();
    private Status status = Status.PROPOSED;

    public EngineeringTask(String title, String intent, String rationale) {
        this(title, intent, rationale, new Proposal(intent, intent,
                "Unrelated changes and autonomous follow-up tasks.",
                List.of("Address the requested outcome and report supporting evidence."),
                "Preserve existing work and stay within the approved request.",
                "Use relevant existing checks and report evidence gaps."), "Deterministic proposal.");
    }

    public EngineeringTask(String title, String intent, String rationale,
                           Proposal proposal, String proposalSource) {
        this.title = requireText(title, "Task title");
        this.intent = requireText(intent, "Task intent");
        this.rationale = rationale == null ? "" : rationale.trim();
        this.proposal = java.util.Objects.requireNonNull(proposal);
        this.proposalSource = requireText(proposalSource, "Proposal source");
    }

    public UUID id() { return id; }
    public String title() { return title; }
    public String intent() { return intent; }
    public String rationale() { return rationale; }
    public Proposal proposal() { return proposal; }
    public String proposalSource() { return proposalSource; }
    public String proposalText() {
        return "Goal:\n" + proposal.goal()
                + "\n\nIn scope:\n" + proposal.scope()
                + "\n\nOut of scope:\n" + proposal.outOfScope()
                + "\n\nAcceptance criteria:\n- " + String.join("\n- ", proposal.acceptanceCriteria())
                + "\n\nConstraints:\n" + proposal.constraints()
                + "\n\nRationale:\n" + rationale
                + "\n\nSuggested verification:\n" + proposal.verificationApproach()
                + "\n\nProposal source:\n" + proposalSource;
    }
    public Instant createdAt() { return createdAt; }
    public Status status() { return status; }

    public void approve() {
        requireStatus(Status.PROPOSED);
        status = Status.APPROVED;
    }

    public void reject() {
        requireStatus(Status.PROPOSED);
        status = Status.REJECTED;
    }

    public void complete() {
        requireStatus(Status.APPROVED);
        status = Status.COMPLETED;
    }

    private void requireStatus(Status expected) {
        if (status != expected) {
            throw new IllegalStateException("Task must be " + expected + " but was " + status);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    public record Proposal(String goal, String scope, String outOfScope,
                           List<String> acceptanceCriteria, String constraints,
                           String verificationApproach) {
        public Proposal {
            goal = requireText(goal, "Goal");
            scope = requireText(scope, "Scope");
            outOfScope = requireText(outOfScope, "Out-of-scope boundary");
            acceptanceCriteria = List.copyOf(java.util.Objects.requireNonNull(acceptanceCriteria));
            if (acceptanceCriteria.isEmpty()) {
                throw new IllegalArgumentException("Acceptance criteria are required");
            }
            acceptanceCriteria = acceptanceCriteria.stream()
                    .map(value -> requireText(value, "Acceptance criterion")).toList();
            constraints = requireText(constraints, "Constraints");
            verificationApproach = requireText(verificationApproach, "Verification approach");
        }
    }
}
