package com.engineos.observation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ObservationPackageServiceTest {
    @TempDir
    Path tempDirectory;

    @Test
    void findsLatestSuccessfulPackageAndZipsItsArtifacts() throws Exception {
        Path packageDirectory = tempDirectory.resolve("20260918T015607Z");
        Files.createDirectories(packageDirectory.resolve("logs"));
        Files.writeString(packageDirectory.resolve("manifest.json"),
                """
                {"result":{"exitCode":0,"succeeded":true}}
                """);
        Files.writeString(packageDirectory.resolve("logs").resolve("maven.stdout.log"), "GREEN");

        var service = new ObservationPackageService(tempDirectory);
        var observation = service.findLatestCompleted().orElseThrow();
        Path zip = service.createZip(observation);

        assertTrue(zip.getFileName().toString()
                .matches("20260918T015607Z-chatgpt-\\d{8}T\\d{6}Z\\.zip"));
        try (ZipFile contents = new ZipFile(zip.toFile())) {
            assertTrue(contents.getEntry("manifest.json") != null);
            assertEquals("GREEN", new String(contents.getInputStream(
                    contents.getEntry("logs/maven.stdout.log")).readAllBytes()));
        }
        assertTrue(Files.isDirectory(packageDirectory));
    }

    @Test
    void ignoresPackagesThatDidNotSucceed() throws Exception {
        Path packageDirectory = tempDirectory.resolve("20260918T015608Z");
        Files.createDirectories(packageDirectory);
        Files.writeString(packageDirectory.resolve("manifest.json"),
                """
                {"result":{"exitCode":1,"succeeded":false}}
                """);

        assertFalse(new ObservationPackageService(tempDirectory).findLatestCompleted().isPresent());
    }

    @Test
    void findsOnlyTheCompletedPackageForTheCurrentRun() throws Exception {
        createPackage("20260918T015609Z", "old-run");
        createPackage("20260918T015610Z", "current-run");

        var service = new ObservationPackageService(tempDirectory);

        assertEquals("current-run",
                service.findCompletedForRun("current-run").orElseThrow().runId());
        assertFalse(service.findCompletedForRun("missing-run").isPresent());
    }

    private void createPackage(String name, String runId) throws Exception {
        Path packageDirectory = tempDirectory.resolve(name);
        Files.createDirectories(packageDirectory);
        Files.writeString(packageDirectory.resolve("manifest.json"),
                "{\"result\":{\"exitCode\":0,\"succeeded\":true}}");
        Files.writeString(packageDirectory.resolve("engineering-result.json"),
                "{\"history\":{\"runId\":\"" + runId + "\"}}");
    }
}
