package com.engineos.infrastructure.git;

import java.time.Instant;

public record GitCommit(
        String hash,
        String shortHash,
        String message,
        String author,
        Instant timestamp) {
}
