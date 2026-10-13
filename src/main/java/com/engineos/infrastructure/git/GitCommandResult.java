package com.engineos.infrastructure.git;

public record GitCommandResult(int exitCode, String standardOutput, String standardError) {
    public boolean succeeded() {
        return exitCode == 0;
    }
}
