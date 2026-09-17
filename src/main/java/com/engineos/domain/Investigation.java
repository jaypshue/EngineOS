package com.engineos.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Investigation {
    private final UUID id = UUID.randomUUID();
    private final String objective;
    private final List<Evidence> evidence = new ArrayList<>();

    public Investigation(String objective) {
        if (objective == null || objective.isBlank()) {
            throw new IllegalArgumentException("Investigation objective is required");
        }
        this.objective = objective.trim();
    }

    public UUID id() {
        return id;
    }

    public String objective() {
        return objective;
    }

    public List<Evidence> evidence() {
        return List.copyOf(evidence);
    }

    public void record(Evidence item) {
        if (item == null) {
            throw new IllegalArgumentException("Evidence is required");
        }
        evidence.add(item);
    }
}
