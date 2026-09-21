package com.engineos.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public final class FileEngineeringHistoryRepository implements EngineeringHistoryRepository {
    private final Path historyDirectory;
    private final ObjectMapper mapper;

    public FileEngineeringHistoryRepository() {
        this(defaultDirectory());
    }

    public FileEngineeringHistoryRepository(Path historyDirectory) {
        this.historyDirectory = historyDirectory.toAbsolutePath().normalize();
        this.mapper = new ObjectMapper()
                .findAndRegisterModules()
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public Path directory() {
        return historyDirectory;
    }

    @Override
    public void save(EngineeringHistoryRecord record) {
        if (record == null || record.runId() == null || record.runId().isBlank()) {
            throw new IllegalArgumentException("A history record with a run ID is required");
        }
        try {
            Files.createDirectories(historyDirectory);
            Path target = historyDirectory.resolve(record.runId() + ".json");
            Path temporary = historyDirectory.resolve(record.runId() + "." + UUID.randomUUID() + ".tmp");
            mapper.writeValue(temporary.toFile(), record);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save engineering history", exception);
        }
    }

    @Override
    public List<EngineeringHistoryRecord> list() {
        if (!Files.isDirectory(historyDirectory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(historyDirectory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::readSafely)
                    .flatMap(Optional::stream)
                    .filter(record -> !record.isSyntheticInternal())
                    .sorted(Comparator.comparing(EngineeringHistoryRecord::completedAt,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        } catch (IOException exception) {
            return List.of();
        }
    }

    @Override
    public Optional<EngineeringHistoryRecord> get(String runId) {
        if (runId == null || runId.isBlank()) {
            return Optional.empty();
        }
        return readSafely(historyDirectory.resolve(runId + ".json"));
    }

    private Optional<EngineeringHistoryRecord> readSafely(Path file) {
        try {
            return Optional.of(mapper.readValue(file.toFile(), EngineeringHistoryRecord.class));
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static Path defaultDirectory() {
        String appData = System.getenv("APPDATA");
        Path base = appData == null || appData.isBlank()
                ? Path.of(System.getProperty("user.home"), "AppData", "Roaming")
                : Path.of(appData);
        return base.resolve("EngineOS").resolve("history");
    }
}
