package com.engineos.history;

import com.engineos.agent.AiUsage;
import com.engineos.domain.CopilotResultDetails;
import com.engineos.observation.ObservationRunner;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record EngineeringHistoryRecord(
        String runId,
        Instant startedAt,
        Instant completedAt,
        Duration duration,
        String engineeringRequest,
        String requestSummary,
        String codingAgent,
        String provider,
        String requestedModel,
        String actualModel,
        String workspace,
        String verificationTarget,
        String finalStatus,
        String codingResultStatus,
        Integer exitCode,
        String sessionId,
        AiUsage aiUsage,
        Long apiDurationMs,
        Long sessionDurationMs,
        Integer linesAdded,
        Integer linesRemoved,
        Integer filesModified,
        List<String> modifiedFiles,
        boolean semanticResultAvailable,
        boolean semanticResultComplete,
        String engineeringResult,
        List<String> activity,
        String observationStatus,
        boolean observationAvailable,
        String observationLocation,
        String verificationSummary,
        String verificationCommand,
        Integer verificationExitCode,
        Duration verificationDuration,
        String verificationOutput,
        String verificationError) {

    public EngineeringHistoryRecord(
            String runId,
            Instant startedAt,
            Instant completedAt,
            Duration duration,
            String engineeringRequest,
            String requestSummary,
            String codingAgent,
            String provider,
            String requestedModel,
            String actualModel,
            String workspace,
            String verificationTarget,
            String finalStatus,
            String codingResultStatus,
            Integer exitCode,
            String sessionId,
            AiUsage aiUsage,
            Long apiDurationMs,
            Long sessionDurationMs,
            Integer linesAdded,
            Integer linesRemoved,
            Integer filesModified,
            List<String> modifiedFiles,
            boolean semanticResultAvailable,
            boolean semanticResultComplete,
            String engineeringResult,
            String observationStatus,
            boolean observationAvailable,
            String observationLocation,
            String verificationSummary) {
        this(runId, startedAt, completedAt, duration, engineeringRequest, requestSummary,
                codingAgent, provider, requestedModel, actualModel, workspace, verificationTarget,
                finalStatus, codingResultStatus, exitCode, sessionId, aiUsage, apiDurationMs,
                sessionDurationMs, linesAdded, linesRemoved, filesModified, modifiedFiles,
                semanticResultAvailable, semanticResultComplete, engineeringResult,
                List.of(), observationStatus, observationAvailable, observationLocation, verificationSummary,
                null, null, null, null, null);
    }

    public EngineeringHistoryRecord(
            String runId,
            Instant startedAt,
            Instant completedAt,
            Duration duration,
            String engineeringRequest,
            String requestSummary,
            String codingAgent,
            String provider,
            String requestedModel,
            String actualModel,
            String workspace,
            String verificationTarget,
            String finalStatus,
            String codingResultStatus,
            Integer exitCode,
            String sessionId,
            AiUsage aiUsage,
            Long apiDurationMs,
            Long sessionDurationMs,
            Integer linesAdded,
            Integer linesRemoved,
            Integer filesModified,
            List<String> modifiedFiles,
            boolean semanticResultAvailable,
            boolean semanticResultComplete,
            String engineeringResult,
            String observationStatus,
            boolean observationAvailable,
            String observationLocation,
            String verificationSummary,
            String verificationCommand,
            Integer verificationExitCode,
            Duration verificationDuration,
            String verificationOutput,
            String verificationError) {
        this(runId, startedAt, completedAt, duration, engineeringRequest, requestSummary,
                codingAgent, provider, requestedModel, actualModel, workspace, verificationTarget,
                finalStatus, codingResultStatus, exitCode, sessionId, aiUsage, apiDurationMs,
                sessionDurationMs, linesAdded, linesRemoved, filesModified, modifiedFiles,
                semanticResultAvailable, semanticResultComplete, engineeringResult, List.of(),
                observationStatus, observationAvailable, observationLocation, verificationSummary,
                verificationCommand, verificationExitCode, verificationDuration,
                verificationOutput, verificationError);
    }

    public EngineeringHistoryRecord {
        engineeringRequest = sanitize(engineeringRequest);
        requestSummary = sanitize(requestSummary);
        engineeringResult = sanitize(engineeringResult);
        activity = activity == null ? List.of() : List.copyOf(activity);
        modifiedFiles = modifiedFiles == null ? List.of() : List.copyOf(modifiedFiles);
        aiUsage = aiUsage == null ? AiUsage.unavailable() : aiUsage;
    }

    public static EngineeringHistoryRecord from(
            String runId,
            Instant startedAt,
            Instant completedAt,
            String request,
            String requestSummary,
            String codingAgent,
            String provider,
            String requestedModel,
            String workspace,
            String verificationTarget,
            String finalStatus,
            String codingResultStatus,
            Integer exitCode,
            String sessionId,
            AiUsage aiUsage,
            CopilotResultDetails details,
            String engineeringResult,
            String observationStatus,
            boolean observationAvailable,
            String observationLocation,
            String verificationSummary) {
        return from(runId, startedAt, completedAt, request, requestSummary, codingAgent, provider,
                requestedModel, workspace, verificationTarget, finalStatus, codingResultStatus,
                exitCode, sessionId, aiUsage, details, engineeringResult, observationStatus,
                observationAvailable, observationLocation, verificationSummary, null, List.of());
    }

    public static EngineeringHistoryRecord from(
            String runId,
            Instant startedAt,
            Instant completedAt,
            String request,
            String requestSummary,
            String codingAgent,
            String provider,
            String requestedModel,
            String workspace,
            String verificationTarget,
            String finalStatus,
            String codingResultStatus,
            Integer exitCode,
            String sessionId,
            AiUsage aiUsage,
            CopilotResultDetails details,
            String engineeringResult,
            String observationStatus,
            boolean observationAvailable,
            String observationLocation,
            String verificationSummary,
            ObservationRunner.ObservationResult observation) {
        return from(runId, startedAt, completedAt, request, requestSummary, codingAgent, provider,
                requestedModel, workspace, verificationTarget, finalStatus, codingResultStatus,
                exitCode, sessionId, aiUsage, details, engineeringResult, observationStatus,
                observationAvailable, observationLocation, verificationSummary, observation, List.of());
    }

    public static EngineeringHistoryRecord from(
            String runId,
            Instant startedAt,
            Instant completedAt,
            String request,
            String requestSummary,
            String codingAgent,
            String provider,
            String requestedModel,
            String workspace,
            String verificationTarget,
            String finalStatus,
            String codingResultStatus,
            Integer exitCode,
            String sessionId,
            AiUsage aiUsage,
            CopilotResultDetails details,
            String engineeringResult,
            String observationStatus,
            boolean observationAvailable,
            String observationLocation,
            String verificationSummary,
            ObservationRunner.ObservationResult observation,
            List<String> activity) {
        var safeDetails = details == null ? CopilotResultDetails.unavailable() : details;
        return new EngineeringHistoryRecord(
                runId, startedAt, completedAt,
                Duration.between(startedAt, completedAt),
                request, requestSummary, codingAgent, provider, requestedModel,
                safeDetails.actualModel() == null && aiUsage != null ? aiUsage.model() : safeDetails.actualModel(),
                workspace, verificationTarget, finalStatus,
                codingResultStatus, exitCode, sessionId, aiUsage,
                safeDetails.totalApiDurationMs(), safeDetails.sessionDurationMs(),
                safeDetails.linesAdded(), safeDetails.linesRemoved(), safeDetails.filesModified(),
                safeDetails.modifiedFiles(), safeDetails.available(), safeDetails.complete(),
                engineeringResult, activity, observationStatus, observationAvailable, observationLocation,
                verificationSummary,
                observation == null ? null : observation.command(),
                observation == null ? null : observation.exitCode(),
                observation == null ? null : observation.duration(),
                observation == null ? null : observation.output(),
                observation == null ? null : observation.error());
    }

    @JsonIgnore
    public String displaySummary() {
        if (engineeringRequest != null) {
            String[] lines = engineeringRequest.split("\\R");
            for (int index = 0; index < lines.length; index++) {
                String line = lines[index];
                String trimmed = line.trim();
                if (trimmed.regionMatches(true, 0, "Goal:", 0, "Goal:".length())) {
                    String goal = trimmed.substring("Goal:".length()).trim();
                    if (goal.isBlank() && index + 1 < lines.length) {
                        goal = lines[++index].trim();
                    }
                    if (!goal.isBlank()) {
                        return goal;
                    }
                }
            }
        }
        if (requestSummary != null && !requestSummary.isBlank()) {
            return requestSummary;
        }
        return engineeringRequest == null || engineeringRequest.isBlank()
                ? "Engineering run"
                : engineeringRequest.replaceAll("\\s+", " ").trim();
    }

    @JsonIgnore
    public boolean isSyntheticInternal() {
        return "test".equalsIgnoreCase(provider)
                && "test".equalsIgnoreCase(codingAgent)
                && !semanticResultAvailable
                && "Make the requested change.".equals(engineeringRequest);
    }

    @JsonIgnore
    public EngineeringAssessment assessment() {
        return EngineeringAssessment.from(this);
    }

    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replaceAll("(?i)(api[-_ ]?key|access[-_ ]?token|password|secret|credential)"
                        + "(\\s*[:=]\\s*)\\S+", "$1$2[REDACTED]")
                .replaceAll("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+", "Bearer [REDACTED]");
    }
}
