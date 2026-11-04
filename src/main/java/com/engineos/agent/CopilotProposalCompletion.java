package com.engineos.agent;

import com.engineos.agent.LlmEngineerAgent.ProposalGenerationException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Separate, tool-disabled Copilot invocation, never the Coder Agent's execution session. */
public final class CopilotProposalCompletion implements LlmEngineerAgent.Completion {
    private final String model;
    private final Duration timeout;

    public CopilotProposalCompletion(String model) {
        this(model, Duration.ofSeconds(60));
    }

    CopilotProposalCompletion(String model, Duration timeout) {
        this.model = model;
        this.timeout = timeout;
    }

    @Override
    public String complete(String prompt) throws ProposalGenerationException {
        if (prompt.length() > 14000) {
            throw new ProposalGenerationException("Engineer prompt exceeds bounded CLI context");
        }
        Path directory = null;
        Process process = null;
        try {
            directory = Files.createTempDirectory("engineos-proposal-");
            Path output = directory.resolve("response.txt");
            Path error = directory.resolve("error.txt");
            process = new ProcessBuilder(commandFor(prompt, model))
                    .directory(directory.toFile())
                    .redirectOutput(output.toFile()).redirectError(error.toFile()).start();
            process.getOutputStream().close();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new ProposalGenerationException("Engineer model timed out");
            }
            if (process.exitValue() != 0) {
                throw new ProposalGenerationException("Engineer CLI exited with code " + process.exitValue());
            }
            if (Files.size(output) > 40000) {
                throw new ProposalGenerationException("Engineer response exceeded size limit");
            }
            return Files.readString(output, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ProposalGenerationException("Unable to run Engineer model", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProposalGenerationException("Engineer model interrupted", exception);
        } finally {
            if (process != null && process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
            if (directory != null) {
                try {
                    Files.deleteIfExists(directory.resolve("response.txt"));
                    Files.deleteIfExists(directory.resolve("error.txt"));
                    Files.deleteIfExists(directory);
                } catch (IOException exception) {
                    System.getLogger(CopilotProposalCompletion.class.getName()).log(
                            System.Logger.Level.WARNING, "Unable to clean Engineer proposal files", exception);
                }
            }
        }
    }

    static List<String> commandFor(String prompt, String model) {
        return List.of(CopilotCoderAgent.resolveCopilotExecutable(),
                CopilotCoderAgent.promptArgument(prompt),
                "--model", model, "--silent", "--stream", "off", "--output-format", "text",
                "--available-tools=", "--deny-tool=*", "--no-custom-instructions", "--no-ask-user",
                "--no-auto-update", "--trust-folder");
    }
}
