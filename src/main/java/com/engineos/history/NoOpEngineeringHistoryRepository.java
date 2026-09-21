package com.engineos.history;

import java.util.List;
import java.util.Optional;

public final class NoOpEngineeringHistoryRepository implements EngineeringHistoryRepository {
    @Override
    public void save(EngineeringHistoryRecord record) {
        // Internal and test runners must not create durable user history.
    }

    @Override
    public List<EngineeringHistoryRecord> list() {
        return List.of();
    }

    @Override
    public Optional<EngineeringHistoryRecord> get(String runId) {
        return Optional.empty();
    }
}
