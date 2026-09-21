package com.engineos.observation;

import com.engineos.domain.EngineeringRequest;
import com.engineos.domain.EngineeringResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Adds the request and final Engineering Result to an observation package
 * after execution has produced them.
 */
public final class ObservationEvidencePackageWriter {
    private static final Pattern PACKAGE_PATH =
            Pattern.compile("(?m)^Observation package:\\s*(.+?)\\s*$");
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private ObservationEvidencePackageWriter() {
    }

    public static void writeIfAvailable(
            ObservationRunner.ObservationResult observation,
            String request,
            EngineeringResult result) {
        if (observation == null || observation.output() == null) {
            return;
        }
        var matcher = PACKAGE_PATH.matcher(observation.output());
        if (!matcher.find()) {
            return;
        }
        Path packageDirectory = Path.of(matcher.group(1).trim()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(packageDirectory.resolve("api"));
            Files.createDirectories(packageDirectory.resolve("processes"));
            Files.createDirectories(packageDirectory.resolve("observation"));
            EngineeringRequest parsed = EngineeringRequest.parse(request);
            DirectedVerificationPlan plan = DirectedVerificationPlan.from(parsed);

            Files.writeString(packageDirectory.resolve("engineering-request.txt"), request == null ? "" : request);
            Files.writeString(packageDirectory.resolve("engineering-result.txt"),
                    MAPPER.writeValueAsString(result));
            Files.writeString(packageDirectory.resolve("engineering-result.json"),
                    MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result));
            Files.writeString(packageDirectory.resolve("verification-plan.json"),
                    MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(plan));
            Files.writeString(packageDirectory.resolve("verification-plan.txt"),
                    String.join(System.lineSeparator(), plan.steps()));
            Files.writeString(packageDirectory.resolve("verification-execution.txt"),
                    executionSummary(observation, result));
            if (result.control() != null) {
                Files.writeString(packageDirectory.resolve("execution-policy.json"),
                        MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result.control()));
            }
            Files.writeString(packageDirectory.resolve("observation/result.json"),
                    MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(observation));
            updateManifest(packageDirectory, plan, result.control() != null);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to enrich observation package: " + packageDirectory
                    + " (" + exception.getMessage() + ")", exception);
        }
    }

    private static String executionSummary(
            ObservationRunner.ObservationResult observation,
            EngineeringResult result) {
        return "Observation exit code: " + observation.exitCode() + System.lineSeparator()
                + "Observation succeeded: " + observation.succeeded() + System.lineSeparator()
                + "Engineering Result: " + result.supervision().status() + System.lineSeparator()
                + "Execution termination: "
                + (result.control() == null ? "UNAVAILABLE" : result.control().termination())
                + System.lineSeparator()
                + "Evidence gaps: " + String.join("; ", result.supervision().evidenceGaps());
    }

    private static void updateManifest(
            Path packageDirectory,
            DirectedVerificationPlan plan,
            boolean executionPolicyAvailable)
            throws IOException {
        Path manifest = packageDirectory.resolve("manifest.json");
        if (!Files.isRegularFile(manifest)) {
            return;
        }
        String manifestText = Files.readString(manifest);
        if (!manifestText.isEmpty() && manifestText.charAt(0) == '\uFEFF') {
            manifestText = manifestText.substring(1);
        }
        Map<String, Object> value = MAPPER.readValue(manifestText, LinkedHashMap.class);
        value.put("engineeringRequest", "engineering-request.txt");
        value.put("engineeringResult", "engineering-result.json");
        value.put("verificationPlan", "verification-plan.json");
        value.put("verificationExecution", "verification-execution.txt");
        if (executionPolicyAvailable) {
            value.put("executionPolicy", "execution-policy.json");
        }
        value.put("verificationCategories", plan.categories());
        Files.writeString(manifest, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value));
    }
}
