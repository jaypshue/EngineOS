package com.engineos.domain;

import java.util.List;

public record CodingTask(
        EngineeringTask engineeringTask,
        List<String> command) {

    public CodingTask {
        if (engineeringTask == null) {
            throw new IllegalArgumentException("Engineering task is required");
        }
        if (engineeringTask.status() != EngineeringTask.Status.APPROVED) {
            throw new IllegalStateException("Only an approved engineering task can become a coding task");
        }
        command = List.copyOf(command == null ? List.of() : command);
    }
}
