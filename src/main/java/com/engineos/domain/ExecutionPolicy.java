package com.engineos.domain;

import java.time.Duration;

public record ExecutionPolicy(
        ExecutionPhase phase,
        Duration maximumDuration,
        int maximumCorrectionAttempts,
        boolean workspaceModificationAllowed,
        boolean stopOnBoundaryViolation) {

    public ExecutionPolicy {
        phase = phase == null ? ExecutionPhase.IMPLEMENTATION : phase;
        maximumDuration = maximumDuration == null || maximumDuration.isNegative()
                || maximumDuration.isZero() ? Duration.ofMinutes(10) : maximumDuration;
        if (maximumCorrectionAttempts < 0) {
            throw new IllegalArgumentException("Maximum correction attempts cannot be negative");
        }
    }

    public static ExecutionPolicy defaults(ExecutionPhase phase) {
        String prefix = phase == ExecutionPhase.VERIFICATION
                ? "engineos.execution.verification"
                : "engineos.execution.implementation";
        return new ExecutionPolicy(
                phase,
                configuredDuration(prefix + ".max-duration-seconds",
                        phase == ExecutionPhase.VERIFICATION ? 120 : 600),
                configuredInt("engineos.execution.max-correction-attempts", 1),
                phase == ExecutionPhase.IMPLEMENTATION,
                true);
    }

    public static ExecutionPolicy forRequest(EngineeringRequest request) {
        String source = (request == null ? "" : request.raw()).toLowerCase();
        String normalizedSource = source.replace('-', ' ');
        boolean verificationOnly = normalizedSource.contains("verification only")
                || source.contains("do not change source")
                || source.contains("do not modify source")
                || source.contains("without changing source files");
        return defaults(verificationOnly ? ExecutionPhase.VERIFICATION : ExecutionPhase.IMPLEMENTATION);
    }

    private static Duration configuredDuration(String key, long fallbackSeconds) {
        return Duration.ofSeconds(configuredLong(key, fallbackSeconds));
    }

    private static int configuredInt(String key, int fallback) {
        return (int) configuredLong(key, fallback);
    }

    private static long configuredLong(String key, long fallback) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            String environment = System.getenv(key.toUpperCase().replace('.', '_').replace('-', '_'));
            value = environment;
        }
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
