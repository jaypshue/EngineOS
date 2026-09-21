package com.engineos.agent;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CopilotCoderAgentTest {
    @Test
    void newRequestCommandDoesNotResumePersistedSession() {
        String prompt = "Create exactly one file named execution-probe.txt containing probe-ok.";

        var command = CopilotCoderAgent.commandFor(prompt, "gpt-5.6-luna",
                Path.of("C:\\workspace\\.engineos-copilot-usage-test.json"));

        assertTrue(command.getFirst().toLowerCase().endsWith("copilot.exe"));
        assertFalse(command.contains("cmd.exe"));
        assertFalse(command.contains("/d"));
        assertFalse(command.contains("/c"));
        assertEquals("--prompt=" + prompt, command.stream()
                .filter(argument -> argument.startsWith("--prompt="))
                .findFirst()
                .orElseThrow());
        assertEquals("gpt-5.6-luna", command.get(command.indexOf("--model") + 1));
        assertTrue(command.contains("--allow-all-tools"));
        assertTrue(command.contains("--allow-all-paths"));
        assertFalse(command.contains("--no-ask-user"));
        assertTrue(command.contains("--output-format"));
        assertTrue(command.contains("--usage-output-file"));
        assertFalse(command.stream().anyMatch(argument -> argument.startsWith("--resume=")));
    }

    @Test
    void multilineEngineeringRequestIsOnePromptArgument() {
        String prompt = """
                ENGINEOS_REQUEST_V1
                project: C:\\workspace\\EngineOS
                model: gpt-5.6-luna
                request:
                  Inspect the current workspace and report findings.
                """;

        var command = CopilotCoderAgent.commandFor(prompt, "gpt-5.6-luna",
                Path.of("C:\\workspace\\.engineos-copilot-usage-test.json"));

        var promptArguments = command.stream()
                .filter(argument -> argument.startsWith("--prompt="))
                .toList();
        assertEquals(1, promptArguments.size());
        assertEquals("--prompt=" + prompt, promptArguments.getFirst());
        assertEquals("gpt-5.6-luna", command.get(command.indexOf("--model") + 1));
        assertEquals(1, command.stream()
                .filter(argument -> argument.contains("ENGINEOS_REQUEST_V1"))
                .count());
    }

    @Test
    void quotedMultilineEngineeringRequestEscapesWindowsPromptQuotes() {
        String prompt = """
                ENGINEOS_REQUEST_V1
                Goal: Replace the text "ENGINEERING CONTROL ENVIRONME..." with complete text.
                Verification: run the relevant UI tests.
                """;

        var command = CopilotCoderAgent.commandFor(prompt, "gpt-5.6-luna",
                Path.of("C:\\workspace\\.engineos-copilot-usage-test.json"));

        assertEquals("--prompt=" + prompt.replace("\"", "\\\""), command.get(1));
        assertEquals("gpt-5.6-luna", command.get(command.indexOf("--model") + 1));
    }

    @Test
    void executionPromptIncludesAdaptiveEvidenceDrivenWorkflow() {
        var task = new com.engineos.domain.EngineeringTask(
                "Implement the request",
                "Make the requested change.",
                "");
        task.approve();
        var prompt = new com.engineos.domain.CodingTask(task, java.util.List.of()).executionPrompt();
        var normalized = prompt.replaceAll("\\s+", " ").toLowerCase();

        assertTrue(prompt.startsWith("Make the requested change."));
        assertTrue(normalized.contains("inspect the current workspace"));
        assertTrue(normalized.contains("run targeted verification"));
        assertTrue(normalized.contains("correct it"));
        assertTrue(normalized.contains("broader automated checks"));
        assertTrue(normalized.contains("evidence gaps"));
        assertTrue(normalized.contains("engineos owns agent, request, process, desktop, service, observation"));
        assertTrue(normalized.contains("do not wait for, poll, monitor, or attempt to prove termination"));
        assertTrue(normalized.contains("never create a self-referential process wait"));
        assertTrue(normalized.contains("bounded task-specific verification"));

        var verificationPrompt = new com.engineos.domain.CodingTask(task, java.util.List.of())
                .executionPrompt(com.engineos.domain.ExecutionPolicy.defaults(
                        com.engineos.domain.ExecutionPhase.VERIFICATION))
                .toLowerCase();
        assertTrue(verificationPrompt.contains("already-running engineos desktop"));
        assertTrue(verificationPrompt.contains("do not launch another desktop"));
        assertTrue(verificationPrompt.contains("fail fast"));
    }
}
