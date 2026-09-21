package com.engineos.agent;

import com.engineos.domain.CodingResult;
import com.engineos.domain.CodingTask;
import com.engineos.domain.ExecutionPolicy;
import com.engineos.infrastructure.WorkspaceSnapshot;
import com.engineos.domain.WorkspaceContext;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class CopilotCoderAgent implements CoderAgent {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(30);

    private final AgentProvider provider;
    private final Duration timeout;
    private final CopilotSessionStore sessionStore;
    private final AtomicReference<Process> activeProcess = new AtomicReference<>();

    public CopilotCoderAgent(AgentProvider provider) {
        this(provider, DEFAULT_TIMEOUT, new CopilotSessionStore());
    }

    CopilotCoderAgent(AgentProvider provider, Duration timeout) {
        this(provider, timeout, new CopilotSessionStore());
    }

    CopilotCoderAgent(AgentProvider provider, Duration timeout, CopilotSessionStore sessionStore) {
        this.provider = provider;
        this.timeout = timeout;
        this.sessionStore = sessionStore;
    }

    @Override
    public AgentProvider provider() {
        return provider;
    }

    @Override
    public CodingResult execute(WorkspaceContext workspace, CodingTask task) {
        return execute(workspace, task, activity -> {
        });
    }

    @Override
    public CodingResult execute(
            WorkspaceContext workspace,
            CodingTask task,
            Consumer<String> activitySink) {
        return execute(workspace, task, provider.modelId(), activitySink);
    }

    @Override
    public CodingResult execute(WorkspaceContext workspace, CodingTask task, String model,
                                Consumer<String> activitySink) {
        return execute(workspace, task, model,
                ExecutionPolicy.defaults(com.engineos.domain.ExecutionPhase.IMPLEMENTATION), activitySink);
    }

    @Override
    public CodingResult execute(WorkspaceContext workspace, CodingTask task, String model,
                                ExecutionPolicy policy, Consumer<String> activitySink) {
        Instant started = Instant.now();
        WorkspaceSnapshot baseline = policy.workspaceModificationAllowed()
                ? null : WorkspaceSnapshot.capture(workspace.root());
        Path usagePath;
        try {
            usagePath = usageFile(workspace);
        } catch (IOException exception) {
            return new CodingResult(1, "", "Unable to create Copilot usage file: " + exception.getMessage(),
                    Duration.between(started, Instant.now()), model, AiUsage.unavailable());
        }
        List<String> command = commandFor(
                task.executionPrompt(policy), model, usagePath);
        try {
            var process = new ProcessBuilder(command)
                    .directory(workspace.root().toFile())
                    .redirectErrorStream(false)
                    .start();
            activeProcess.set(process);
            CompletableFuture<String> output = CompletableFuture.supplyAsync(
                    () -> readOutput(process.getInputStream(), activitySink, true));
            CompletableFuture<String> error = CompletableFuture.supplyAsync(
                    () -> readOutput(process.getErrorStream(), activity -> {
                    }, false));
            Instant deadline = started.plus(minimum(timeout, policy.maximumDuration()));
            boolean timedOut = false;
            boolean boundaryViolation = false;
            while (process.isAlive()) {
                if (Instant.now().isAfter(deadline)) {
                    timedOut = true;
                    break;
                }
                if (!policy.workspaceModificationAllowed()
                        && !WorkspaceSnapshot.capture(workspace.root()).changedSince(baseline).isEmpty()) {
                    boundaryViolation = true;
                    break;
                }
                if (process.waitFor(100, TimeUnit.MILLISECONDS)) {
                    break;
                }
            }
            if (timedOut || boundaryViolation) {
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
                String reason = boundaryViolation
                        ? "Execution boundary violation: workspace modification prohibited."
                        : "Execution timed out after " + policy.maximumDuration() + ".";
                return result(boundaryViolation ? 125 : 124, output.getNow(""), reason,
                        started, usagePath, model);
            }
            var result = result(process.exitValue(), output.get(), error.get(), started, usagePath, model);
            if (!result.succeeded()) {
                result = withError(result, failureDiagnostics(command, result.standardOutput(),
                        result.standardError()));
            }
            var session = sessionId(result.standardOutput()).orElse(null);
            if (session != null) {
                sessionStore.save(workspace, session);
            }
            return withSession(result, session);
        } catch (IOException exception) {
            return new CodingResult(1, "", "Unable to start Copilot: " + exception.getMessage(),
                    Duration.between(started, Instant.now()), model, AiUsage.unavailable());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            Process interruptedProcess = activeProcess.get();
            if (interruptedProcess != null) {
                interruptedProcess.destroy();
                if (interruptedProcess.isAlive()) {
                    interruptedProcess.destroyForcibly();
                }
            }
            return new CodingResult(130, "", "Copilot interrupted.",
                    Duration.between(started, Instant.now()), provider.modelId(), AiUsage.unavailable());
        } catch (java.util.concurrent.ExecutionException exception) {
            return new CodingResult(1, "", "Unable to read Copilot output: " + exception.getCause(),
                    Duration.between(started, Instant.now()), provider.modelId(), AiUsage.unavailable());
        }
        finally {
            activeProcess.set(null);
        }
    }

    @Override
    public boolean cancel() {
        Process process = activeProcess.get();
        if (process == null) {
            return false;
        }
        process.destroy();
        if (process.isAlive()) {
            process.destroyForcibly();
        }
        return true;
    }

    private static Duration minimum(Duration first, Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    static List<String> commandFor(String prompt, String model, Path usagePath) {
        return List.of(
                resolveCopilotExecutable(),
                promptArgument(prompt),
                "--model", model,
                "--allow-all-tools",
                "--allow-all-paths",
                "--output-format", "json",
                "--usage-output-file", usagePath.toString());
    }

    static String promptArgument(String prompt) {
        return "--prompt=" + (prompt == null ? "" : prompt.replace("\"", "\\\""));
    }

    static String resolveCopilotExecutable() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            Path candidate = Path.of(appData, "npm", "node_modules", "@github", "copilot",
                    "node_modules", "@github", "copilot-win32-x64", "copilot.exe");
            if (Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().normalize().toString();
            }
        }

        Path userProfileCandidate = Path.of(System.getProperty("user.home"), "AppData", "Roaming",
                "npm", "node_modules", "@github", "copilot", "node_modules", "@github",
                "copilot-win32-x64", "copilot.exe");
        if (Files.isRegularFile(userProfileCandidate)) {
            return userProfileCandidate.toAbsolutePath().normalize().toString();
        }

        return "copilot.exe";
    }

    private static java.util.Optional<String> sessionId(String output) {
        var matcher = java.util.regex.Pattern.compile("\\\"sessionId\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
                .matcher(output);
        String value = null;
        while (matcher.find()) {
            value = matcher.group(1);
        }
        return java.util.Optional.ofNullable(value);
    }

    private CodingResult result(int exitCode, String output, String error, Instant started, Path usagePath,
                                String model) {
        UsageFileReader.SidecarData sidecar = UsageFileReader.read(usagePath, model);
        AiUsage usage = sidecar.usage();
        if (!usage.available()) {
            usage = CopilotResultParser.parseUsage(output, model);
        }
        var details = mergeDetails(CopilotResultParser.parse(output), sidecar);
        return new CodingResult(exitCode, output, error, Duration.between(started, Instant.now()),
                model, usage, null,
                details);
    }

    static com.engineos.domain.CopilotResultDetails mergeDetails(
            com.engineos.domain.CopilotResultDetails stdout,
            UsageFileReader.SidecarData sidecar) {
        if (!sidecar.semanticAvailable()) {
            return stdout;
        }
        return new com.engineos.domain.CopilotResultDetails(
                true,
                stdout.actualModel() == null ? sidecar.usage().model() : stdout.actualModel(),
                stdout.totalApiDurationMs() == null
                        ? sidecar.totalApiDurationMs() : stdout.totalApiDurationMs(),
                stdout.sessionDurationMs(),
                stdout.linesAdded() == null ? sidecar.linesAdded() : stdout.linesAdded(),
                stdout.linesRemoved() == null ? sidecar.linesRemoved() : stdout.linesRemoved(),
                stdout.filesModified() == null ? sidecar.filesModified() : stdout.filesModified(),
                stdout.filesModified() == null ? sidecar.modifiedFiles() : stdout.modifiedFiles());
    }

    private static CodingResult withSession(CodingResult result, String sessionId) {
        return new CodingResult(result.exitCode(), result.standardOutput(), result.standardError(), result.duration(),
                result.requestedModel(), result.usage(), sessionId, result.details());
    }

    private static CodingResult withError(CodingResult result, String error) {
        return new CodingResult(result.exitCode(), result.standardOutput(), error, result.duration(),
                result.requestedModel(), result.usage(), result.sessionId(), result.details());
    }

    private static String failureDiagnostics(List<String> command, String output, String error) {
        return "Copilot invocation failed.\n"
                + "Invocation: " + formatCommand(command) + "\n"
                + "STDERR:\n" + (error == null || error.isBlank() ? "(empty)" : error.trim()) + "\n"
                + "STDOUT:\n" + (output == null || output.isBlank() ? "(empty)" : output.trim());
    }

    static String formatCommand(List<String> command) {
        return command.stream()
                .map(argument -> argument.indexOf(' ') >= 0
                        ? "\"" + argument.replace("\"", "\\\"") + "\""
                        : argument)
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private static Path usageFile(WorkspaceContext workspace) throws IOException {
        return Files.createTempFile(workspace.root(), ".engineos-copilot-usage-", ".json");
    }

    private static String readOutput(InputStream stream, Consumer<String> activitySink, boolean publishActivity) {
        var output = new StringBuilder();
        try {
            try (var reader = new BufferedReader(new InputStreamReader(stream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                    if (publishActivity) {
                        CopilotActivityNormalizer.normalize(line).ifPresent(activitySink);
                    }
                }
            }
            return output.toString();
        } catch (IOException exception) {
            return "Unable to read Copilot output: " + exception.getMessage();
        }
    }

}
