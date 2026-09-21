package com.engineos.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record ExecutionControl(
        ExecutionPhase phase,
        ExecutionPolicy policy,
        Instant startedAt,
        Instant endedAt,
        String termination,
        boolean workspaceModificationDetected,
        List<String> changedFiles,
        int correctionAttempts,
        List<ExecutionPhase> phaseTransitions) {

    public ExecutionControl {
        phase = phase == null ? ExecutionPhase.IMPLEMENTATION : phase;
        termination = termination == null || termination.isBlank() ? "COMPLETED" : termination;
        changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
        phaseTransitions = phaseTransitions == null ? List.of(phase) : List.copyOf(phaseTransitions);
    }

    public Duration duration() {
        if (startedAt == null || endedAt == null) {
            return null;
        }
        return Duration.between(startedAt, endedAt);
    }
}
