package com.engineos.ui;

import com.engineos.agent.AiUsage;
import com.engineos.history.EngineeringHistoryRecord;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservationWindowTest {
    @Test
    void completionRetainsActivityInsteadOfClearingIt() {
        assertEquals("Activity retained in Engineering History \u2014 2 events. Use HISTORY to review.",
                ObservationWindow.activityRetentionText(List.of("Inspecting files", "Copilot completed.")));
    }

    @Test
    void completionHasExplicitEmptyActivityState() {
        assertEquals("No Coding Agent activity was recorded for this run.",
                ObservationWindow.activityRetentionText(List.of()));
    }

    @Test
    void currentResultMustMatchTheCurrentRunAndContainResultText() {
        var record = new EngineeringHistoryRecord(
                "run-current", Instant.now(), Instant.now(), Duration.ZERO,
                "request", "summary", "agent", "provider", "model", "actual",
                "C:\\projects\\EngineOS", "MAVEN", "GREEN", "complete", 0, "session",
                AiUsage.unavailable(), 1L, 1L, 1, 0, 1, List.of("file.java"),
                true, true, "ENGINEERING RESULT", List.of(), "SUCCEEDED", true, null,
                "verified", "mvn test", 0, Duration.ZERO, "passed", "");

        assertTrue(ObservationWindow.currentResultMatches("run-current", record));
        assertFalse(ObservationWindow.currentResultMatches("run-old", record));
        assertFalse(ObservationWindow.currentResultMatches("run-current",
                new EngineeringHistoryRecord(
                        "run-current", Instant.now(), Instant.now(), Duration.ZERO,
                        "request", "summary", "agent", "provider", "model", "actual",
                        "C:\\projects\\EngineOS", "MAVEN", "GREEN", "complete", 0, "session",
                        AiUsage.unavailable(), 1L, 1L, 1, 0, 1, List.of("file.java"),
                        true, true, "", List.of(), "SUCCEEDED", true, null,
                        "verified", "mvn test", 0, Duration.ZERO, "passed", "")));
    }

    @Test
    void cancelledRunDoesNotExposeAnEngineeringResultAsCurrent() {
        var record = new EngineeringHistoryRecord(
                "run-cancelled", Instant.now(), Instant.now(), Duration.ZERO,
                "request", "summary", "agent", "provider", "model", "actual",
                "C:\\projects\\EngineOS", "MAVEN", "CANCELLED", "cancelled", 130, "session",
                AiUsage.unavailable(), 1L, 1L, 0, 0, 0, List.of(),
                true, true, "structured terminal record", List.of(), "NOT_RUN", false, null,
                "cancelled", null, null, Duration.ZERO, "", "");

        assertFalse(ObservationWindow.currentResultMatches("run-cancelled", record));
    }

    @Test
    void headerSubtitleIsComplete() {
        assertEquals("ENGINEERING CONTROL ENVIRONMENT", ObservationWindow.HEADER_SUBTITLE);
    }
}
