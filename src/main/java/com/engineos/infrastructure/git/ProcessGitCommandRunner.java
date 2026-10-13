package com.engineos.infrastructure.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ProcessGitCommandRunner implements GitCommandRunner {
    @Override
    public GitCommandResult run(Path workspace, List<String> arguments)
            throws IOException, InterruptedException {
        var command = new ArrayList<String>();
        command.add("git");
        command.addAll(arguments);
        var process = new ProcessBuilder(command)
                .directory(workspace.toFile())
                .redirectErrorStream(false)
                .start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        return new GitCommandResult(process.waitFor(), output, error);
    }
}
