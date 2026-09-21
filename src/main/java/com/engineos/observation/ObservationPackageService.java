package com.engineos.observation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ObservationPackageService {
    private static final Pattern SUCCESSFUL_MANIFEST =
            Pattern.compile("\"succeeded\"\\s*:\\s*true");
    private static final Pattern RUN_ID =
            Pattern.compile("\"runId\"\\s*:\\s*\"([^\"]+)\"");
    private static final DateTimeFormatter ZIP_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                    .withZone(ZoneOffset.UTC);

    private final Path observationsRoot;

    public ObservationPackageService(Path observationsRoot) {
        this.observationsRoot = observationsRoot.toAbsolutePath().normalize();
    }

    public Optional<CompletedObservation> findLatestCompleted() {
        if (!Files.isDirectory(observationsRoot)) {
            return Optional.empty();
        }

        try (Stream<Path> packages = Files.list(observationsRoot)) {
            return packages
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .map(this::completedObservation)
                    .flatMap(Optional::stream)
                    .findFirst();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to inspect observation packages", exception);
        }
    }

    public Optional<CompletedObservation> findCompletedForRun(String runId) {
        if (runId == null || runId.isBlank() || !Files.isDirectory(observationsRoot)) {
            return Optional.empty();
        }
        try (Stream<Path> packages = Files.list(observationsRoot)) {
            return packages
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .map(this::completedObservation)
                    .flatMap(Optional::stream)
                    .filter(observation -> runId.equals(observation.runId()))
                    .findFirst();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to inspect observation packages", exception);
        }
    }

    public Path createZip(CompletedObservation observation) {
        Path packageDirectory = observation.directory();
        if (!isCompleted(packageDirectory)) {
            throw new IllegalArgumentException("Observation package is not complete: " + packageDirectory);
        }
        try {
            Files.createDirectories(observationsRoot);
            Path zip = observationsRoot.resolve(
                    packageDirectory.getFileName() + "-chatgpt-" + ZIP_TIMESTAMP.format(Instant.now()) + ".zip");
            try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
                try (Stream<Path> files = Files.walk(packageDirectory)) {
                    files.filter(Files::isRegularFile)
                            .sorted()
                            .forEach(path -> addToZip(output, packageDirectory, path));
                }
            }
            return zip;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create observation ZIP", exception);
        }
    }

    private Optional<CompletedObservation> completedObservation(Path directory) {
        Path manifest = directory.resolve("manifest.json");
        return Files.isRegularFile(manifest) && isSuccessfulManifest(manifest)
                ? Optional.of(new CompletedObservation(directory, manifest, runId(directory)))
                : Optional.empty();
    }

    private String runId(Path directory) {
        Path result = directory.resolve("engineering-result.json");
        if (!Files.isRegularFile(result)) {
            return null;
        }
        try {
            var matcher = RUN_ID.matcher(Files.readString(result));
            return matcher.find() ? matcher.group(1) : null;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read Engineering Result: " + result, exception);
        }
    }

    private boolean isCompleted(Path directory) {
        return Files.isDirectory(directory)
                && Files.isRegularFile(directory.resolve("manifest.json"))
                && isSuccessfulManifest(directory.resolve("manifest.json"));
    }

    private boolean isSuccessfulManifest(Path manifest) {
        try {
            return SUCCESSFUL_MANIFEST.matcher(Files.readString(manifest)).find();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read observation manifest: " + manifest, exception);
        }
    }

    private static void addToZip(ZipOutputStream output, Path root, Path file) {
        String entryName = root.relativize(file).toString().replace('\\', '/');
        try {
            output.putNextEntry(new ZipEntry(entryName));
            Files.copy(file, output);
            output.closeEntry();
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to add observation file to ZIP: " + file, exception);
        }
    }
}
