package com.engineos.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotActivityNormalizerTest {
    @Test
    void suppressesRawProtocolEventsAndInternalMetadata() {
        assertTrue(CopilotActivityNormalizer.normalize(
                "{\"type\":\"assistant.tool_call_delta\",\"toolCallId\":\"abc\",\"inputDelta\":\"...\"}").isEmpty());
        assertTrue(CopilotActivityNormalizer.normalize(
                "{\"type\":\"session_store_sql\",\"sessionId\":\"abc\"}").isEmpty());
        assertTrue(CopilotActivityNormalizer.normalize(
                "{\"type\":\"model.call_finished\",\"callId\":\"abc\"}").isEmpty());
    }

    @Test
    void formatsFileToolCallWithPath() {
        assertEquals("Reading src/main/App.java...",
                CopilotActivityNormalizer.normalize(
                        "{\"type\":\"assistant.tool_call\",\"toolName\":\"read_file\","
                                + "\"arguments\":{\"path\":\"src/main/App.java\"}}").orElseThrow());
        assertEquals("Editing src/main/App.java...",
                CopilotActivityNormalizer.normalize(
                        "{\"type\":\"assistant.tool_call\",\"toolName\":\"apply_patch\","
                                + "\"arguments\":{\"filePath\":\"src/main/App.java\"}}").orElseThrow());
        assertEquals("Running tests: npm test",
                CopilotActivityNormalizer.normalize(
                        "{\"type\":\"assistant.tool_call\",\"data\":{\"toolName\":\"terminal\","
                                + "\"arguments\":\"{\\\"command\\\":\\\"npm test\\\"}\"}}").orElseThrow());
    }

    @Test
    void formatsTerminalAndTestActivity() {
        assertEquals("Running tests: mvn clean test",
                CopilotActivityNormalizer.normalize(
                        "{\"type\":\"assistant.tool_call\",\"toolName\":\"terminal\","
                                + "\"arguments\":{\"command\":\"mvn clean test\"}}").orElseThrow());
        assertEquals("Running tests...",
                CopilotActivityNormalizer.normalize(
                        "{\"type\":\"assistant.tool_call\",\"toolName\":\"run_tests\"}").orElseThrow());
    }

    @Test
    void preservesUsefulAssistantAndOrdinaryTextButSuppressesMalformedProtocol() {
        assertEquals("I am checking the workspace.",
                CopilotActivityNormalizer.normalize(
                        "{\"type\":\"assistant.message\",\"message\":\"I am checking the workspace.\"}").orElseThrow());
        assertEquals("Inspecting workspace",
                CopilotActivityNormalizer.normalize("Inspecting workspace").orElseThrow());
        assertTrue(CopilotActivityNormalizer.normalize("{\"type\":\"assistant.message\"").isEmpty());
    }
}
