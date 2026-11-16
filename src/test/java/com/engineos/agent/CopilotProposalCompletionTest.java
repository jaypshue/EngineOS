package com.engineos.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CopilotProposalCompletionTest {
    @Test
    void proposalCallDisablesToolsAndDoesNotUseCoderPermissionsOrSessions() {
        var command = CopilotProposalCompletion.commandFor("Return a proposal.", "auto");

        assertEquals("auto", command.get(command.indexOf("--model") + 1));
        assertTrue(command.contains("--available-tools="));
        assertTrue(command.contains("--no-custom-instructions"));
        assertTrue(command.contains("--no-ask-user"));
        assertEquals("off", command.get(command.indexOf("--stream") + 1));
        assertEquals("json", command.get(command.indexOf("--output-format") + 1));
        assertFalse(command.stream().anyMatch(argument -> argument.startsWith("--prompt=")));
        assertFalse(command.contains("--deny-tool=*"));
        assertFalse(command.contains("--trust-folder"));
        assertFalse(command.contains("--allow-all-tools"));
        assertFalse(command.contains("--allow-all-paths"));
        assertFalse(command.contains("--resume"));
    }

    @Test
    void extractsAssistantProposalFromCopilotJsonlTelemetry() throws Exception {
        String proposal = """
                {"type":"session.start","data":{}}
                {"type":"assistant.message","data":{"content":"{\\"title\\":\\"Inspect\\",\\"goal\\":\\"Understand\\"}"}}
                {"type":"result","data":{"success":true}}
                """;

        assertEquals("{\"title\":\"Inspect\",\"goal\":\"Understand\"}",
                CopilotProposalCompletion.extractAssistantContent(proposal));
    }

    @Test
    void rejectsJsonlWithoutAssistantProposal() {
        var failure = assertThrows(LlmEngineerAgent.ProposalGenerationException.class,
                () -> CopilotProposalCompletion.extractAssistantContent(
                        "{\"type\":\"result\",\"data\":{\"success\":true}}"));
        assertTrue(failure.getMessage().contains("no assistant proposal"));
    }

    @Test
    void oversizedPromptIsRejectedBeforeStartingAnyProcess() {
        var failure = assertThrows(LlmEngineerAgent.ProposalGenerationException.class,
                () -> new CopilotProposalCompletion("auto").complete("x".repeat(14001)));
        assertTrue(failure.getMessage().contains("bounded CLI context"));
    }
}
