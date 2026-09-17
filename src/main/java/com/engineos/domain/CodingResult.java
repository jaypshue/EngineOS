package com.engineos.domain;

import java.time.Duration;

public record CodingResult(
        int exitCode,
        String standardOutput,
        String standardError,
        Duration duration) {

    public boolean succeeded() {
        return exitCode == 0;
    }

    public String summary() {
        return succeeded()
                ? "Coder completed successfully (exit code 0)."
                : "Coder failed (exit code " + exitCode + ").";
    }
}
