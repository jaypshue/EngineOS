package com.engineos.infrastructure.git;

public record GitStageResult(
        boolean succeeded,
        String output,
        String error) {
    public GitStageResult {
        output = output == null ? "" : output;
        error = error == null ? "" : error;
    }
}
