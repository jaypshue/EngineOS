package com.engineos.infrastructure.git;

public record GitCommitResult(boolean succeeded, String commitHash, String output, String error) {
}
