package com.engineos.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotResultParserTest {
    @Test
    void parsesResultEventDetails() {
        var details = CopilotResultParser.parse("""
                {"type":"result","model":"gpt-5.6-luna","totalApiDurationMs":1200,"sessionDurationMs":1800,"codeChanges":{"linesAdded":12,"linesRemoved":3,"filesModified":["src/ExecutionRecord.java","src/ExecutionRecordTest.java"]}}
                """);

        assertEquals("gpt-5.6-luna", details.actualModel());
        assertEquals(1200L, details.totalApiDurationMs());
        assertEquals(1800L, details.sessionDurationMs());
        assertEquals(12, details.linesAdded());
        assertEquals(3, details.linesRemoved());
        assertEquals(2, details.filesModified());
        assertEquals(2, details.modifiedFiles().size());
        assertTrue(details.complete());
    }

    @Test
    void retainsTheLastResultAfterActivityEvents() {
        var details = CopilotResultParser.parse("""
                {"type":"assistant.message","message":"Inspecting the workspace"}
                {"type":"result","model":"first","codeChanges":{"linesAdded":1,"linesRemoved":0,"filesModified":[]}}
                {"type":"tool.completed","tool":"write"}
                {"type":"result","model":"actual","codeChanges":{"linesAdded":4,"linesRemoved":2,"filesModified":3}}
                """);

        assertEquals("actual", details.actualModel());
        assertEquals(4, details.linesAdded());
        assertEquals(2, details.linesRemoved());
        assertEquals(3, details.filesModified());
    }

    @Test
    void toleratesMissingOptionalFields() {
        var details = CopilotResultParser.parse("""
                {"type":"result","model":"gpt-5.6-luna","codeChanges":{"linesAdded":0,"linesRemoved":0,"filesModified":[]}}
                """);

        assertTrue(details.available());
        assertEquals("gpt-5.6-luna", details.actualModel());
        assertEquals(0, details.linesAdded());
        assertEquals(0, details.linesRemoved());
        assertEquals(0, details.filesModified());
        assertEquals(null, details.totalApiDurationMs());
        assertFalse(details.complete());
    }

    @Test
    void ignoresMalformedAndUnexpectedJsonlSafely() {
        var details = CopilotResultParser.parse("""
                not json
                {"type":"assistant.message","message":"done"}
                {"type":"result","model":"usable","codeChanges":{"linesAdded":1,"linesRemoved":0,"filesModified":[]}}
                {malformed
                """);

        assertTrue(details.available());
        assertEquals("usable", details.actualModel());
        assertFalse(CopilotResultParser.parse("not json").available());
    }

    @Test
    void parsesUsageFromResultEventWhenSidecarUsageIsUnavailable() {
        var usage = CopilotResultParser.parseUsage("""
                {"type":"result","model":"gpt-5.6-luna","usage":{"totalNanoAiu":9200000000,"inputTokens":10,"outputTokens":20,"cacheReadTokens":3,"reasoningTokens":4}}
                """, "auto");

        assertTrue(usage.available());
        assertEquals("gpt-5.6-luna", usage.model());
        assertEquals("9.2000", usage.aiCredits());
        assertEquals("10", usage.inputTokens());
    }

    @Test
    void treatsZeroCreditsAsUnavailableWhileKeepingOtherUsage() {
        var usage = CopilotResultParser.parseUsage("""
                {"type":"result","model":"gpt-5.6-luna","usage":{"totalNanoAiu":0,"inputTokens":10}}
                """, "auto");

        assertEquals(null, usage.aiCredits());
        assertEquals("10", usage.inputTokens());
    }
}
