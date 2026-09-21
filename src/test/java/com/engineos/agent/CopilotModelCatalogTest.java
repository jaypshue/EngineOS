package com.engineos.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotModelCatalogTest {
    @Test
    void includesDefaultModelChoices() {
        var models = CopilotModelCatalog.fromOutput("{}");

        assertEquals(List.of("auto", "gpt-5.6-luna"), models);
    }

    @Test
    void retainsRuntimeReportedActiveModel() {
        var models = CopilotModelCatalog.fromOutput(
                "{\"lastActiveModel\":\"claude-sonnet-5\"}");

        assertTrue(models.contains("auto"));
        assertTrue(models.contains("gpt-5.6-luna"));
        assertTrue(models.contains("claude-sonnet-5"));
    }
}
