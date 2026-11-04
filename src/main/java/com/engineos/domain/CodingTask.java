package com.engineos.domain;

import java.util.List;

public record CodingTask(
        EngineeringTask engineeringTask,
        List<String> command) {

    public CodingTask {
        if (engineeringTask == null) {
            throw new IllegalArgumentException("Engineering task is required");
        }
        if (engineeringTask.status() != EngineeringTask.Status.APPROVED) {
            throw new IllegalStateException("Only an approved engineering task can become a coding task");
        }
        command = List.copyOf(command == null ? List.of() : command);
    }

    /**
     * Gives coding agents the adaptive execution policy without changing the
     * original Engineering Request text.
     */
    public String executionPrompt() {
        return executionPrompt(null);
    }

    public String executionPrompt(ExecutionPolicy policy) {
        String verificationBoundary = policy != null
                && policy.phase() == ExecutionPhase.VERIFICATION
                ? """

                VERIFICATION-ONLY TARGETING
                Treat the already-running EngineOS Desktop as the verification target when the request
                describes Desktop behavior. Do not launch another Desktop or Service Host. Do not perform
                broad process, port, filesystem, or environment reconnaissance before attempting the
                requested UI action. Locate the target with the smallest bounded check, attempt the
                requested action promptly, and fail fast with a clear target-not-found result if it is
                unavailable. Do not close or terminate the existing Desktop.
                """
                : "";
        return engineeringTask.intent()
                + "\n\nHUMAN-APPROVED ENGINEERING TASK\n"
                + engineeringTask.proposalText()
                + "\nThe original request and EngineOS execution policy remain authoritative. "
                + "Do not interpret suggested verification as permission to exceed those boundaries."
                + "\n\n"
                + """
                ENGINEOS EXECUTION WORKFLOW
                Work evidence-first and adapt to the request. Before editing, inspect the current workspace,
                repository state, and existing implementation so you reuse what already exists and avoid
                duplicate architecture. Make the smallest change that satisfies the request. After a
                meaningful change, run targeted verification. If it exposes a defect, correct it and
                re-run the targeted verification. Once targeted verification is sound, run broader
                automated checks when they are relevant. Report what was implemented, what was actually
                verified, what failed, and any important evidence gaps. Do not perform unnecessary steps
                for a trivial request, and do not treat a successful build alone as proof of behavioral
                acceptance.

                EngineOS owns agent, request, process, Desktop, Service, observation, and evidence
                lifecycle verification. Do not wait for, poll, monitor, or attempt to prove termination
                of your own process, and never create a self-referential process wait. Perform only the
                requested engineering task and bounded task-specific verification.
                """
                + verificationBoundary;
    }
}
