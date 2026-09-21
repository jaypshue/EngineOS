package com.engineos.domain;

import com.engineos.agent.AiUsage;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodingResultTest {
    @Test
    void reportIncludesActualModelAndAiCredits() {
        var result = new CodingResult(
                0,
                "",
                "",
                Duration.ZERO,
                "gpt-5.6-luna",
                new AiUsage("gpt-5.6-luna", "9.2000", "10", "20", "3", "4", null, null));

        var report = result.report();

        assertTrue(report.contains("Model: gpt-5.6-luna"));
        assertTrue(report.contains("AI credits: 9.2000"));
        assertTrue(report.contains("Input tokens: 10"));
        assertTrue(report.contains("Output tokens: 20"));
    }

    @Test
    void reportMarksCreditsUnavailableWithoutInventingUsage() {
        var result = new CodingResult(
                0, "", "", Duration.ZERO, "gpt-5.6-luna", AiUsage.unavailable());

        var report = result.report();

        assertTrue(report.contains("Model: gpt-5.6-luna"));
        assertTrue(report.contains("AI credits: unavailable"));
    }

    @Test
    void exposesSemanticCopilotResultDetails() {
        var result = new CodingResult(
                0,
                "{\"type\":\"result\"}",
                "",
                Duration.ZERO,
                "gpt-5.6-luna",
                AiUsage.unavailable(),
                "session-a",
                new CopilotResultDetails(true, "gpt-5.6-luna", 1200L, 1800L, 4, 2, 1,
                        java.util.List.of("src/ExecutionRecord.java")));

        assertTrue(result.semanticResultAvailable());
        assertEquals("gpt-5.6-luna", result.details().actualModel());
        assertEquals(4, result.details().linesAdded());
        assertEquals(1, result.details().filesModified());
        assertTrue(result.report().contains("Code changes: +4 / -2 lines, 1 files"));
    }

    @Test
    void distinguishesProcessFailureFromUnavailableSemanticResult() {
        var result = new CodingResult(7, "", "failure", Duration.ZERO);

        assertTrue(result.report().contains("Coder failed (exit code 7)."));
        assertTrue(result.report().contains("Semantic Copilot result: unavailable"));
        assertFalse(result.semanticResultAvailable());
    }
}
