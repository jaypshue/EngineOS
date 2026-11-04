package com.engineos.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CopilotProposalCompletionTest {
    @Test
    void proposalCallDisablesToolsAndDoesNotUseCoderPermissionsOrSessions() {
        var command = CopilotProposalCompletion.commandFor("Return a proposal.", "auto");

        assertEquals("--prompt=Return a proposal.", command.get(1));
        assertEquals("auto", command.get(command.indexOf("--model") + 1));
        assertTrue(command.contains("--available-tools="));
        assertTrue(command.contains("--deny-tool=*"));
        assertTrue(command.contains("--no-custom-instructions"));
        assertTrue(command.contains("--no-ask-user"));
        assertTrue(command.contains("--trust-folder"));
        assertEquals("off", command.get(command.indexOf("--stream") + 1));
        assertEquals("text", command.get(command.indexOf("--output-format") + 1));
        assertFalse(command.contains("--allow-all-tools"));
        assertFalse(command.contains("--allow-all-paths"));
        assertFalse(command.contains("--resume"));
    }

    @Test
    void oversizedPromptIsRejectedBeforeStartingAnyProcess() {
        var failure = assertThrows(LlmEngineerAgent.ProposalGenerationException.class,
                () -> new CopilotProposalCompletion("auto").complete("x".repeat(14001)));
        assertTrue(failure.getMessage().contains("bounded CLI context"));
    }
}
