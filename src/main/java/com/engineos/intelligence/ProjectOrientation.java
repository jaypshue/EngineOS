package com.engineos.intelligence;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public record ProjectOrientation(
        WholeRepository wholeRepository,
        ApplicationArchitecture applicationArchitecture,
        List<Subsystem> relevantSubsystems,
        List<RelevantType> relevantTypes,
        List<String> executionPath,
        List<Change> currentChange,
        Verification verification,
        List<Change> recentEngineeringHistory) {

    public ProjectOrientation {
        relevantSubsystems = relevantSubsystems == null ? List.of() : List.copyOf(relevantSubsystems);
        relevantTypes = relevantTypes == null ? List.of() : List.copyOf(relevantTypes);
        executionPath = executionPath == null ? List.of() : List.copyOf(executionPath);
        currentChange = currentChange == null ? List.of() : List.copyOf(currentChange);
        recentEngineeringHistory = recentEngineeringHistory == null
                ? List.of() : List.copyOf(recentEngineeringHistory);
    }

    public ProjectOrientation(
            WholeRepository wholeRepository,
            ApplicationArchitecture applicationArchitecture,
            List<Subsystem> relevantSubsystems,
            List<RelevantType> relevantTypes,
            List<String> executionPath,
            List<Change> currentChange,
            Verification verification) {
        this(wholeRepository, applicationArchitecture, relevantSubsystems, relevantTypes,
                executionPath, currentChange, verification, List.of());
    }

    public record WholeRepository(
            Path root,
            String identity,
            List<String> languages,
            List<String> frameworks,
            List<String> majorDirectories,
            List<String> buildAndConfigurationFiles,
            List<String> uncertainties) {
    }

    public record ApplicationArchitecture(
            List<String> components,
            List<String> relationships,
            List<String> uncertainties) {
    }

    public record Subsystem(
            String name,
            String path,
            String evidence) {
    }

    public record RelevantType(
            String name,
            String kind,
            String path,
            String packageName) {
    }

    public record Change(
            String runId,
            Instant completedAt,
            String status,
            String outcome,
            boolean verified,
            Integer linesAdded,
            Integer linesRemoved,
            List<String> modifiedFiles) {
        public Change {
            modifiedFiles = modifiedFiles == null ? List.of() : List.copyOf(modifiedFiles);
        }
    }

    public record Verification(
            String status,
            List<String> evidence,
            List<String> uncertainties) {
        public Verification {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
            uncertainties = uncertainties == null ? List.of() : List.copyOf(uncertainties);
        }
    }
}
