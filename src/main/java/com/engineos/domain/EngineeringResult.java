package com.engineos.domain;

import com.engineos.agent.AiUsage;
import com.engineos.history.EngineeringAssessment;

import java.time.Duration;
import java.util.List;

/**
 * Structured handoff of one completed EngineOS engineering run.
 */
public record EngineeringResult(
        String request,
        Execution execution,
        List<String> activity,
        Changes changes,
        Verification verification,
        Observation observation,
        History history,
        Supervision supervision,
        ExecutionControl control,
        EngineeringAssessment assessment) {

    public EngineeringResult(
            String request,
            Execution execution,
            List<String> activity,
            Changes changes,
            Verification verification,
            Observation observation,
            History history,
            Supervision supervision) {
        this(request, execution, activity, changes, verification, observation, history, supervision, null, null);
    }

    public EngineeringResult(
            String request,
            Execution execution,
            List<String> activity,
            Changes changes,
            Verification verification,
            Observation observation,
            History history,
            Supervision supervision,
            ExecutionControl control) {
        this(request, execution, activity, changes, verification, observation, history, supervision, control, null);
    }

    public EngineeringResult {
        activity = activity == null ? List.of() : List.copyOf(activity);
    }

    public record Execution(
            String codingAgent,
            String requestedModel,
            String actualModel,
            String sessionId,
            Duration duration,
            AiUsage usage) {
    }

    public record Changes(
            Integer linesAdded,
            Integer linesRemoved,
            Integer filesModified,
            List<String> modifiedFiles) {
        public Changes {
            modifiedFiles = modifiedFiles == null ? List.of() : List.copyOf(modifiedFiles);
        }
    }

    public record Verification(
            String status,
            String output,
            String error,
            boolean succeeded) {
    }

    public record Observation(
            String status,
            String output,
            String error,
            boolean available,
            String reference) {
    }

    public record History(
            String runId,
            boolean persisted) {
    }

    public record Supervision(
            String status,
            boolean evidenceSufficient,
            List<String> uncertainties,
            String implementationStatus) {
        public Supervision(
                String status,
                boolean evidenceSufficient,
                List<String> uncertainties) {
            this(status, evidenceSufficient, uncertainties, "UNKNOWN");
        }

        public Supervision {
            uncertainties = uncertainties == null ? List.of() : List.copyOf(uncertainties);
            implementationStatus = implementationStatus == null || implementationStatus.isBlank()
                    ? "UNKNOWN"
                    : implementationStatus;
        }

        public List<String> evidenceGaps() {
            return uncertainties;
        }
    }
}
