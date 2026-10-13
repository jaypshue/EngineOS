package com.engineos.infrastructure.git;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@FunctionalInterface
public interface GitCommandRunner {
    GitCommandResult run(Path workspace, List<String> arguments) throws IOException, InterruptedException;
}
