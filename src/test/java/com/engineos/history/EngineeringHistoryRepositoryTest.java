package com.engineos.history;

import com.engineos.agent.AiUsage;
import com.engineos.domain.CopilotResultDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineeringHistoryRepositoryTest {
    @TempDir
    Path directory;

    @Test
    void savesAndLoadsStructuredHistoryAcrossRepositoryRecreation() {
        var repository = new FileEngineeringHistoryRepository(directory);
        var record = record("run-1", Instant.parse("2026-09-18T20:00:00Z"), "ENGINEERING_REQUEST_V1\nDo work.");

        repository.save(record);

        var recreated = new FileEngineeringHistoryRepository(directory);
        var loaded = recreated.get("run-1").orElseThrow();
        assertEquals(record.engineeringRequest(), loaded.engineeringRequest());
        assertEquals(record.linesAdded(), loaded.linesAdded());
        assertEquals(record.modifiedFiles(), loaded.modifiedFiles());
        assertEquals(record.aiUsage(), loaded.aiUsage());
    }

    @Test
    void savesAndLoadsCodingAgentActivityAcrossRepositoryRecreation() {
        var repository = new FileEngineeringHistoryRepository(directory);
        var record = EngineeringHistoryRecord.from(
                "activity-run", Instant.parse("2026-09-18T20:00:00Z"),
                Instant.parse("2026-09-18T20:01:00Z"), "ENGINEERING_REQUEST_V1\nDo work.",
                "Do work.", "GitHub Copilot", "github", "gpt-5.6-luna",
                "C:\\project", "MAVEN", "GREEN", "Coder completed successfully", 0,
                "session-1", AiUsage.unavailable(), null, "Engineering result",
                "SUCCEEDED", true, null, "MAVEN", new com.engineos.observation.ObservationRunner
                        .ObservationResult(0, "passed", "", "mvnw.cmd test", Duration.ofSeconds(1)),
                List.of("Inspecting files", "Running tests...", "Copilot completed."));

        repository.save(record);

        var recreated = new FileEngineeringHistoryRepository(directory);
        assertEquals(List.of("Inspecting files", "Running tests...", "Copilot completed."),
                recreated.get("activity-run").orElseThrow().activity());
    }

    @Test
    void accumulatesRecordsInReverseCompletionOrder() {
        var repository = new FileEngineeringHistoryRepository(directory);
        repository.save(record("older", Instant.parse("2026-09-18T20:00:00Z"), "older"));
        repository.save(record("newer", Instant.parse("2026-09-18T21:00:00Z"), "newer"));

        assertEquals(List.of("newer", "older"),
                repository.list().stream().map(EngineeringHistoryRecord::runId).toList());
    }

    @Test
    void skipsOnlyCorruptIndividualRecords() throws Exception {
        var repository = new FileEngineeringHistoryRepository(directory);
        repository.save(record("valid", Instant.parse("2026-09-18T20:00:00Z"), "valid"));
        Files.writeString(directory.resolve("corrupt.json"), "{not-json");

        assertEquals(List.of("valid"),
                repository.list().stream().map(EngineeringHistoryRecord::runId).toList());
        assertTrue(repository.get("corrupt").isEmpty());
    }

    @Test
    void excludesOnlyKnownSyntheticTestRecordsFromNormalHistoryList() {
        var repository = new FileEngineeringHistoryRepository(directory);
        repository.save(EngineeringHistoryRecord.from(
                "synthetic", Instant.now().minusSeconds(1), Instant.now(),
                "Make the requested change.", "Make the requested change.",
                "test", "test", null, null, null, "GREEN",
                "Coder process completed (exit code 0); semantic result unavailable.",
                0, null, com.engineos.agent.AiUsage.unavailable(), null,
                "synthetic", "SUCCEEDED", true, null, null));
        repository.save(record("real", Instant.now(), "A real engineering request"));

        assertEquals(List.of("real"),
                repository.list().stream().map(EngineeringHistoryRecord::runId).toList());
        assertTrue(repository.get("synthetic").isPresent());
    }

    @Test
    void doesNotPersistRawOutputOrSecrets() throws Exception {
        var repository = new FileEngineeringHistoryRepository(directory);
        repository.save(record("safe", Instant.now(), "request api_key=secret-value"));

        String json = Files.readString(directory.resolve("safe.json"));
        assertFalse(json.contains("API_KEY"));
        assertFalse(json.contains("secret-value"));
        assertFalse(json.contains("standardOutput"));
    }

    private static EngineeringHistoryRecord record(String id, Instant completed, String request) {
        return EngineeringHistoryRecord.from(
                id, completed.minusSeconds(4), completed, request, request,
                "GitHub Copilot", "github", "requested-model", "C:\\project", "MAVEN",
                "GREEN", "Coder completed successfully", 0, "session-1",
                new AiUsage("actual-model", "2", "3", "4", "5", "6", null, null),
                new CopilotResultDetails(true, "actual-model", 120L, 240L,
                        10, 2, 1, List.of("src/App.java")),
                "Engineering result", "SUCCEEDED", true, null, "Maven tests passed");
    }
}
