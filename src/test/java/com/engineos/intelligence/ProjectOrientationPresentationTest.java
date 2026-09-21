package com.engineos.intelligence;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectOrientationPresentationTest {
    @Test
    void formatsEveryOrientationSectionAndExplicitUnknowns() {
        var orientation = new ProjectOrientation(
                new ProjectOrientation.WholeRepository(
                        Path.of("fixture"), "fixture", List.of("Java"), List.of(),
                        List.of("src"), List.of("pom.xml"), List.of("Framework unavailable")),
                new ProjectOrientation.ApplicationArchitecture(
                        List.of("src: application source"), List.of(), List.of()),
                List.of(new ProjectOrientation.Subsystem("src", "src", "fixture evidence")),
                List.of(new ProjectOrientation.RelevantType("App", "class", "src/App.java", "example")),
                List.of("Runtime entry point unavailable."),
                List.of(),
                new ProjectOrientation.Verification(
                        "UNAVAILABLE", List.of(), List.of("No verification record was found.")));

        String payload = ProjectOrientationPresentation.format(orientation);

        assertTrue(payload.startsWith("PROJECT ORIENTATION"));
        assertTrue(payload.contains("WHOLE REPOSITORY"));
        assertTrue(payload.contains("APPLICATION ARCHITECTURE"));
        assertTrue(payload.contains("RELEVANT SUBSYSTEMS"));
        assertTrue(payload.contains("RELEVANT TYPES"));
        assertTrue(payload.contains("EXECUTION PATH"));
        assertTrue(payload.contains("CURRENT CHANGE"));
        assertTrue(payload.contains("VERIFICATION"));
        assertTrue(payload.contains("UNKNOWN / UNAVAILABLE"));
        assertTrue(payload.contains("Framework unavailable"));
        assertTrue(payload.contains("No verification record was found."));
    }
}
