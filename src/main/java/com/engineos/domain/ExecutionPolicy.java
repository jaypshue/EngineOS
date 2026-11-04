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
                        phase == ExecutionPhase.VERIFICATION ? 120
                                : phase == ExecutionPhase.INVESTIGATION ? 600 : 600),
                configuredInt("engineos.execution.max-correction-attempts", 1),
                phase == ExecutionPhase.IMPLEMENTATION,
                true);
    }

    public static ExecutionPolicy forRequest(EngineeringRequest request) {
        String source = (request == null ? "" : request.raw()).toLowerCase();
        String normalizedSource = source.replace('-', ' ');
        boolean nonModifying = isNonModifyingRequest(request);
        boolean investigation = nonModifying && (source.contains("investigat")
                || source.contains("existing system")
                || source.contains("existing repository")
                || source.contains("read only")
                || source.contains("readonly"));
        boolean verificationOnly = !investigation && (normalizedSource.contains("verification only")
                || source.contains("do not change source")
                || source.contains("do not modify source")
                || source.contains("without changing source files"));
        return defaults(investigation ? ExecutionPhase.INVESTIGATION
                : verificationOnly ? ExecutionPhase.VERIFICATION : ExecutionPhase.IMPLEMENTATION);
    }

    public static boolean isNonModifyingRequest(EngineeringRequest request) {
        String source = request == null || request.raw() == null
                ? "" : request.raw().toLowerCase().replace('-', ' ');
        return source.contains("do not modify")
                || source.contains("do not change")
                || source.contains("read only")
                || source.contains("readonly")
                || source.contains("investigate only")
                || source.contains("investigation only")
                || source.contains("without changing")
                || source.contains("without modifying")
                || source.contains("verification only")
                || source.contains("do not change source")
                || source.contains("do not modify source")
                || source.contains("without changing source files");
    }

    public boolean nonModifying() {
        return !workspaceModificationAllowed();
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
