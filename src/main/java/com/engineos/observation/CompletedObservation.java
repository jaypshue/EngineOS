package com.engineos.observation;

import java.nio.file.Path;

public record CompletedObservation(Path directory, Path manifest, String runId) {
    public CompletedObservation(Path directory, Path manifest) {
        this(directory, manifest, null);
    }
}
