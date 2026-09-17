package com.engineos.agent;

import com.engineos.domain.*;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

public final class LocalCoderAgent implements CoderAgent {
    private final AgentProvider provider;

    public LocalCoderAgent(AgentProvider provider) {
        this.provider = provider;
    }

    @Override
    public AgentProvider provider() {
        return provider;
    }

    @Override
    public CodingResult execute(WorkspaceContext workspace, CodingTask task) {
        if (task.command().isEmpty()) {
            return new CodingResult(0, "No command requested; coding task boundary verified.", "", Duration.ZERO);
        }
        var started = Instant.now();
        try {
            var process = new ProcessBuilder(task.command())
                    .directory(workspace.root().toFile())
                    .redirectErrorStream(false)
                    .start();
            var output = new String(process.getInputStream().readAllBytes());
            var error = new String(process.getErrorStream().readAllBytes());
            var exitCode = process.waitFor();
            return new CodingResult(exitCode, output, error, Duration.between(started, Instant.now()));
        } catch (IOException exception) {
            return new CodingResult(1, "", exception.getMessage(), Duration.between(started, Instant.now()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new CodingResult(130, "", "Command interrupted.", Duration.between(started, Instant.now()));
        }
    }
}
