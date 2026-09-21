package com.engineos.history;

import java.util.List;
import java.util.Optional;

public interface EngineeringHistoryRepository {
    void save(EngineeringHistoryRecord record);

    List<EngineeringHistoryRecord> list();

    Optional<EngineeringHistoryRecord> get(String runId);
}
