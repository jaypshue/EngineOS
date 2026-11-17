package com.engineos.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestBoundaryTest {
    @Test
    void supportsPathGlobsAndReportsOnlyOutOfBoundaryFiles() {
        var boundary = RequestBoundary.fromDeclaration("src/ui/**");

        assertEquals(List.of("src/main/java/com/engineos/domain/Task.java"),
                boundary.violations(List.of(
                        "src/ui/ObservationWindow.java",
                        "src/main/java/com/engineos/domain/Task.java")));
    }

    @Test
    void supportsFreeTextAndNegativeTerms() {
        var frontend = RequestBoundary.fromDeclaration("frontend only");
        assertTrue(frontend.violations(List.of("src/frontend/App.tsx")).isEmpty());
        assertEquals(List.of("src/backend/Api.java"),
                frontend.violations(List.of("src/backend/Api.java")));

        var infrastructure = RequestBoundary.fromDeclaration("do not touch infrastructure");
        assertEquals(List.of("infrastructure/terraform/main.tf"),
                infrastructure.violations(List.of("src/domain/Task.java", "infrastructure/terraform/main.tf")));
    }

    @Test
    void supportsDottedPackageNames() {
        var boundary = RequestBoundary.fromDeclaration("package com.engineos.ui");

        assertEquals(List.of("src/main/java/com/engineos/domain/Task.java"),
                boundary.violations(List.of(
                        "src/main/java/com/engineos/ui/ObservationWindow.java",
                        "src/main/java/com/engineos/domain/Task.java")));
    }

    @Test
    void preservesReportedPathWhenBoundaryIsViolated() {
        var boundary = RequestBoundary.fromDeclaration("src/ui/**");

        assertEquals(List.of("SRC\\domain\\Task.java"),
                        boundary.violations(List.of("SRC\\domain\\Task.java")));
    }
}
