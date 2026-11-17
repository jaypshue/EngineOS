package com.engineos.infrastructure.git;

public record GitPushResult(
        boolean succeeded,
        String output,
        String error) {
    public GitPushResult {
        output = output == null ? "" : output;
        error = error == null ? "" : error;
    }
}
