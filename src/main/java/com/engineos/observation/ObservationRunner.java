package com.engineos.observation;

import com.engineos.domain.WorkspaceContext;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

public interface ObservationRunner {
    ObservationResult run(Path project);

    default ObservationResult run(WorkspaceContext workspace) {
        return run(workspace.root());
    }

    default VerificationTarget resolveTarget(
            WorkspaceContext workspace,
            Optional<VerificationTarget.Kind> requestedTarget) {
        if (requestedTarget.isEmpty()) {
            try {
                return VerificationTarget.from(workspace);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        VerificationTarget current = VerificationTarget.from(workspace);
        if (requestedTarget.get() == current.kind()) {
            return current;
        }
        throw new IllegalArgumentException(
                "Verification target " + requestedTarget.get()
                        + " is not available for workspace " + workspace.root());
    }

    default ObservationResult run(WorkspaceContext workspace, VerificationTarget target) {
        return run(workspace);
    }

    default ObservationResult run(
            WorkspaceContext workspace,
            VerificationTarget target,
            boolean existingTarget) {
        return run(workspace, target);
    }

    default boolean showActiveTarget() {
        return false;
    }

    record ObservationResult(
            int exitCode,
            String output,
            String error,
            String command,
            Duration duration) {
        public ObservationResult(int exitCode, String output, String error) {
            this(exitCode, output, error, null, null);
        }

        public boolean succeeded() {
            return exitCode == 0;
        }
    }
}
