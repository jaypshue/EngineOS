package com.engineos.observation;

import com.engineos.domain.WorkspaceContext;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class PowerShellObservationRunner implements ObservationRunner {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);

    private final Path engineOsRoot;
    private final Path script;
    private final Duration timeout;
    private final Path intAssistRoot;
    private volatile VerificationTarget activeTarget;
    private volatile Set<Long> preExistingJavaProcessIds;

    public PowerShellObservationRunner(Path engineOsRoot) {
        this(engineOsRoot, engineOsRoot.toAbsolutePath().normalize().getParent()
                .resolve("intassist"), Duration.ofMinutes(10));
    }

    PowerShellObservationRunner(Path engineOsRoot, Duration timeout) {
        this(engineOsRoot, engineOsRoot.toAbsolutePath().normalize().getParent()
                .resolve("intassist"), timeout);
    }

    PowerShellObservationRunner(Path engineOsRoot, Path intAssistRoot, Duration timeout) {
        this.engineOsRoot = engineOsRoot.toAbsolutePath().normalize();
        this.script = this.engineOsRoot.resolve("scripts").resolve("capture-intassist-observation.ps1");
        this.timeout = timeout;
        this.intAssistRoot = intAssistRoot.toAbsolutePath().normalize();
    }

    @Override
    public ObservationResult run(Path project) {
        try {
            return run(new com.engineos.infrastructure.RepositoryDiscovery().load(project));
        } catch (RuntimeException exception) {
            return new ObservationResult(1, "", exception.getMessage());
        }
    }

    @Override
    public ObservationResult run(WorkspaceContext workspace) {
        return run(workspace, resolveTarget(workspace, Optional.empty()));
    }

    @Override
    public VerificationTarget resolveTarget(
            WorkspaceContext workspace,
            Optional<VerificationTarget.Kind> requestedTarget) {
        VerificationTarget current = VerificationTarget.from(workspace);
        if (requestedTarget.isEmpty() || requestedTarget.get() == current.kind()) {
            return current;
        }
        Path targetRoot = switch (requestedTarget.get()) {
            case ENGINEOS -> engineOsRoot;
            case INTASSIST -> intAssistRoot;
            case MAVEN -> workspace.root();
        };
        if (!java.nio.file.Files.isDirectory(targetRoot)) {
            throw new IllegalArgumentException(
                    "Verification target workspace does not exist: " + targetRoot);
        }
        return new VerificationTarget(
                requestedTarget.get(),
                targetRoot,
                switch (requestedTarget.get()) {
                    case ENGINEOS -> "EngineOS";
                    case INTASSIST -> "IntAssist";
                    case MAVEN -> "Maven";
                });
    }

    @Override
    public ObservationResult run(WorkspaceContext workspace, VerificationTarget target) {
        return run(workspace, target, false);
    }

    @Override
    public ObservationResult run(
            WorkspaceContext workspace,
            VerificationTarget target,
            boolean existingTarget) {
        if (target.kind() == VerificationTarget.Kind.MAVEN) {
            return runMavenVerification(target);
        }
        Instant started = Instant.now();
        activeTarget = target;
        preExistingJavaProcessIds = existingTarget ? Set.of() : snapshotJavaProcessIds();
        try {
            var command = new java.util.ArrayList<>(java.util.List.of(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-File", script.toString(),
                    "-OutputRoot", engineOsRoot.resolve("observations").toString(),
                    "-TargetRoot", target.workspace().toString(),
                    "-TargetKind", target.kind().name(),
                    "-NoForeground"));
            if (existingTarget) {
                command.add("-ObserveExistingTarget");
            }
            var process = new ProcessBuilder(command)
                    .directory(engineOsRoot.toFile())
                    .redirectErrorStream(false)
                    .start();
            CompletableFuture<String> output = CompletableFuture.supplyAsync(
                    () -> read(process.getInputStream()));
            CompletableFuture<String> error = CompletableFuture.supplyAsync(
                    () -> read(process.getErrorStream()));
            if (!process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new ObservationResult(124, output.getNow(""), "Observation capture timed out.");
            }
            return new ObservationResult(process.exitValue(), output.get(), error.get());
        } catch (IOException exception) {
            return new ObservationResult(1, "", "Unable to start observation capture: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ObservationResult(130, "", "Observation capture interrupted.");
        } catch (java.util.concurrent.ExecutionException exception) {
            return new ObservationResult(1, "", "Unable to read observation output: " + exception.getCause());
        } finally {
            activeTarget = null;
            preExistingJavaProcessIds = Set.of();
        }
    }

    @Override
    public boolean showActiveTarget() {
        VerificationTarget target = activeTarget;
        if (target == null) {
            return false;
        }
        String escapedTitle = target.windowTitle().replace("'", "''");
        Set<Long> existingProcessIds = preExistingJavaProcessIds;
        if (existingProcessIds == null) {
            return false;
        }
        String excludedIds = existingProcessIds.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
        String command = "$p=Get-Process java,javaw -ErrorAction SilentlyContinue | "
                + "Where-Object {$_.MainWindowTitle -eq '" + escapedTitle + "' -and $_.MainWindowHandle -ne 0 "
                + " -and @(" + excludedIds + ") -notcontains $_.Id}; "
                + "if (@($p).Count -ne 1) { exit 1 }; "
                + "$sig='[DllImport(\"user32.dll\")] public static extern bool SetForegroundWindow(IntPtr hWnd);'; "
                + "Add-Type -MemberDefinition $sig -Name Native -Namespace EngineOS; "
                + "[EngineOS.Native]::SetForegroundWindow($p.MainWindowHandle) | Out-Null";
        try {
            var process = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-Command", command)
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static Set<Long> snapshotJavaProcessIds() {
        try {
            Process process = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                    "(Get-Process java,javaw -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id)")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            String output = read(process.getInputStream());
            if (process.exitValue() != 0) {
                return null;
            }
            return java.util.Arrays.stream(output.split("\\R"))
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .map(Long::parseLong)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        } catch (IOException | InterruptedException | NumberFormatException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private static String read(java.io.InputStream stream) {
        try {
            return new String(stream.readAllBytes());
        } catch (IOException exception) {
            return "Unable to read process output: " + exception.getMessage();
        }
    }

    private ObservationResult runMavenVerification(VerificationTarget target) {
        Instant started = Instant.now();
        var command = target.verificationCommand();
        try {
            var process = new ProcessBuilder(command)
                    .directory(target.workspace().toFile())
                    .redirectErrorStream(true)
                    .start();
            CompletableFuture<String> output = CompletableFuture.supplyAsync(
                    () -> read(process.getInputStream()));
            if (!process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new ObservationResult(124, output.getNow(""), "Maven verification timed out.",
                        formatCommand(command), Duration.between(started, Instant.now()));
            }
            String capturedOutput = output.get();
            int exitCode = process.exitValue();
            String error = exitCode == 0 ? "" : meaningfulDiagnostic(capturedOutput);
            return new ObservationResult(exitCode, capturedOutput, error,
                    formatCommand(command), Duration.between(started, Instant.now()));
        } catch (IOException exception) {
            return new ObservationResult(
                    1, "", "Unable to start Maven verification: " + exception.getMessage(),
                    formatCommand(command), Duration.between(started, Instant.now()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ObservationResult(130, "", "Maven verification interrupted.",
                    formatCommand(command), Duration.between(started, Instant.now()));
        } catch (java.util.concurrent.ExecutionException exception) {
            return new ObservationResult(
                    1, "", "Unable to read Maven verification output: " + exception.getCause(),
                    formatCommand(command), Duration.between(started, Instant.now()));
        }
    }

    private static String formatCommand(java.util.List<String> command) {
        return command.stream()
                .map(argument -> argument.indexOf(' ') >= 0 ? "\"" + argument + "\"" : argument)
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private static String meaningfulDiagnostic(String output) {
        if (output == null || output.isBlank()) {
            return "Maven verification failed without diagnostic output.";
        }
        return output.trim();
    }
}
