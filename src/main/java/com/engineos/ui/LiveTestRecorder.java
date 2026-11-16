package com.engineos.ui;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

/**
 * Records the Windows desktop with an already-installed ffmpeg executable.
 */
final class LiveTestRecorder {
    static final Duration DEFAULT_DURATION = Duration.ofSeconds(60);
    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'X'").withZone(java.time.ZoneOffset.UTC);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "engineos-live-test-recorder");
        thread.setDaemon(true);
        return thread;
    });
    private Process process;
    private Path video;
    private Path workspace;
    private String request;
    private String runId;
    private CaptureTarget target;
    private Instant startedAt;
    private Consumer<RecordingResult> completion;
    private Process hotkeyProcess;
    private Path hotkeyScriptFile;
    private ScheduledFuture<?> targetMonitor;
    private String captureCommand;
    private Path captureStderrFile;
    private boolean fallbackAttempted;
    private boolean stopRequested;
    private WindowInfo selectedWindow;

    synchronized boolean isRecording() {
        return process != null;
    }

    synchronized long remainingSeconds() {
        if (startedAt == null) {
            return 0;
        }
        long elapsed = Duration.between(startedAt, Instant.now()).toSeconds();
        return Math.max(0, DEFAULT_DURATION.toSeconds() - elapsed);
    }

    void startHotkeyMonitor(Runnable callback) {
        stopHotkeyMonitor();
        Thread monitor = new Thread(() -> {
            try {
                Path script = Files.createTempFile("engineos-live-test-hotkey-", ".ps1");
                Files.writeString(script, HOTKEY_SCRIPT, StandardCharsets.UTF_8);
                synchronized (this) {
                    hotkeyScriptFile = script;
                }
                Process listener = new ProcessBuilder(
                        "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                        "-File", script.toString()).redirectErrorStream(true).start();
                synchronized (this) {
                    hotkeyProcess = listener;
                }
                try (var reader = listener.inputReader(StandardCharsets.UTF_8)) {
                    String line;
                    while (listener.isAlive() && (line = reader.readLine()) != null) {
                        if ("ENGINEOS_HOTKEY|REGISTERED".equals(line.trim())) {
                            logHotkey("registered");
                        } else if ("ENGINEOS_HOTKEY|PRESSED".equals(line.trim())) {
                            logHotkey("pressed");
                            callback.run();
                        } else if (line.trim().startsWith("ENGINEOS_HOTKEY|REGISTRATION_FAILED|")) {
                            logHotkey(line.trim());
                        }
                    }
                }
            } catch (IOException exception) {
                logHotkey("startup-failed: " + exception.getMessage());
            } finally {
                synchronized (this) {
                    hotkeyProcess = null;
                    if (hotkeyScriptFile != null) {
                        try {
                            Files.deleteIfExists(hotkeyScriptFile);
                        } catch (IOException ignored) {
                            // The temporary script can be removed by the operating system later.
                        }
                        hotkeyScriptFile = null;
                    }
                }
            }
        }, "engineos-live-test-hotkey");
        monitor.setDaemon(true);
        monitor.start();
    }

    private static void logHotkey(String event) {
        System.out.println("EngineOS live-test hotkey " + event);
        DesktopDiagnostics.stage("live-test-hotkey-" + event.replace(' ', '-'));
    }

    synchronized void stopHotkeyMonitor() {
        if (hotkeyProcess != null) {
            hotkeyProcess.destroy();
            hotkeyProcess = null;
        }
        if (hotkeyScriptFile != null) {
            try {
                Files.deleteIfExists(hotkeyScriptFile);
            } catch (IOException ignored) {
                // The monitor will clean up after it exits.
            }
            hotkeyScriptFile = null;
        }
    }

    synchronized void start(
            Path workspace,
            String request,
            String runId,
            CaptureTarget target,
            Consumer<RecordingResult> completion) {
        start(workspace, request, runId, target, null, completion);
    }

    synchronized void start(
            Path workspace,
            String request,
            String runId,
            CaptureTarget target,
            WindowInfo selectedWindow,
            Consumer<RecordingResult> completion) {
        if (isRecording()) {
            throw new IllegalStateException("A live test recording is already running.");
        }
        if (workspace == null || !Files.isDirectory(workspace)) {
            throw new IllegalArgumentException("Workspace directory does not exist: " + workspace);
        }
        requireFfmpeg();
        try {
            Path outputDirectory = workspace.toAbsolutePath().normalize().resolve(".engineos").resolve("live-tests");
            Files.createDirectories(outputDirectory);
            this.workspace = workspace.toAbsolutePath().normalize();
            this.request = request == null ? "" : request;
            this.runId = runId;
            this.target = target == null ? targetFor(workspace) : target;
            this.selectedWindow = selectedWindow;
            if (selectedWindow != null && !windowBounds(selectedWindow)
                    .map(bounds -> bounds.width > 0 && bounds.height > 0).orElse(false)) {
                this.target = this.target.desktopFallback("The selected window bounds were unavailable.");
            }
            this.startedAt = Instant.now();
            String stem = "live-test-" + FILE_TIMESTAMP.format(Instant.now()) + "-" + java.util.UUID.randomUUID();
            this.video = outputDirectory.resolve(stem + ".mp4");
            this.completion = completion;
            launchCapture();
            Process recording = this.process;
            scheduler.schedule(() -> stop(recording), DEFAULT_DURATION.toSeconds(), TimeUnit.SECONDS);
            if (selectedWindow != null) {
                targetMonitor = scheduler.scheduleWithFixedDelay(
                        () -> monitorTarget(selectedWindow), 500, 500, TimeUnit.MILLISECONDS);
            }
            Thread monitor = new Thread(() -> await(recording), "engineos-live-test-monitor");
            monitor.setDaemon(true);
            monitor.start();
        } catch (IOException exception) {
            clearState();
            throw new IllegalStateException("Unable to start ffmpeg screen capture: " + exception.getMessage(), exception);
        }
    }

    private void launchCapture() throws IOException {
        String captureInput = target.windowTitle().isEmpty() ? "desktop"
                : selectedWindow == null ? "title=" + target.windowTitle().orElseThrow()
                : "hwnd=" + selectedWindow.handle();
        List<String> command = List.of(
                    "ffmpeg", "-y",
                    "-f", "gdigrab",
                    "-framerate", "15",
                    "-i", captureInput,
                    "-vf", "pad=ceil(iw/2)*2:ceil(ih/2)*2",
                    "-c:v", "libx264",
                    "-preset", "ultrafast",
                    "-pix_fmt", "yuv420p",
                    "-t", Long.toString(DEFAULT_DURATION.toSeconds()),
                    video.toString());
        captureCommand = commandLine(command);
        captureStderrFile = video.resolveSibling(video.getFileName()
                + (target.windowTitle().isPresent() ? ".window.ffmpeg.log" : ".desktop.ffmpeg.log"));
        this.process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(captureStderrFile.toFile())
                    .start();
        logCapture("started: " + captureCommand);
    }

    private static String commandLine(List<String> command) {
        return command.stream()
                .map(argument -> argument.contains(" ") ? "\"" + argument + "\"" : argument)
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private void logCapture(String message) {
        System.err.println("EngineOS live-test ffmpeg " + message);
        DesktopDiagnostics.stage("live-test-ffmpeg " + message);
    }

    private void monitorTarget(WindowInfo window) {
        boolean processAlive = ProcessHandle.of(window.processId())
                .map(ProcessHandle::isAlive).orElse(false);
        if (!processAlive || !isWindowOpen(window.handle())) {
            synchronized (this) {
                if (window != selectedWindow) {
                    return;
                }
                logWindowDiagnostic("target closed; stopping recording: " + window.title());
                stop();
            }
        }
    }

    private static boolean isWindowOpen(long handle) {
        String command = """
                Add-Type @'
                using System;
                using System.Runtime.InteropServices;
                public static class EngineOsIsWindow {
                  [DllImport("user32.dll")] public static extern bool IsWindow(IntPtr handle);
                }
                '@
                [EngineOsIsWindow]::IsWindow([IntPtr]%d)
                """.formatted(handle);
        try {
            return "true".equalsIgnoreCase(runWindowScript(command).trim());
        } catch (IOException exception) {
            logWindowDiagnostic("Unable to check target window: " + exception.getMessage());
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logWindowDiagnostic("Target window check interrupted");
            return true;
        }
    }

    synchronized void stop() {
        stopRequested = true;
        stop(process);
    }

    private void stop(Process recording) {
        if (recording == null || !recording.isAlive()) {
            return;
        }
        try {
            recording.getOutputStream().write("q\n".getBytes(StandardCharsets.UTF_8));
            recording.getOutputStream().flush();
        } catch (IOException exception) {
            recording.destroy();
        }
    }

    private void await(Process recording) {
        int exitCode;
        try {
            exitCode = recording.waitFor();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            exitCode = -1;
        }
        RecordingResult result;
        Consumer<RecordingResult> callback;
        synchronized (this) {
            if (recording != process) {
                return;
            }
            String failedCommand = captureCommand;
            String failedStderr = "";
            if (exitCode != 0) {
                try {
                    failedStderr = Files.readString(captureStderrFile, StandardCharsets.UTF_8);
                } catch (IOException exception) {
                    failedStderr = "Unable to read stderr at " + captureStderrFile + ": " + exception.getMessage();
                }
                logCapture("failed with exit code " + exitCode + "; command: " + failedCommand
                        + System.lineSeparator() + "stderr: " + failedStderr);
            }
            if (exitCode != 0 && target != null && target.windowTitle().isPresent()
                    && !fallbackAttempted && !stopRequested) {
                fallbackAttempted = true;
                target = target.desktopFallback("Window capture failed; fell back to full desktop capture.");
                try {
                    startedAt = Instant.now();
                    launchCapture();
                    Process retry = process;
                    scheduler.schedule(() -> stop(retry), DEFAULT_DURATION.toSeconds(), TimeUnit.SECONDS);
                    Thread retryMonitor = new Thread(() -> await(retry), "engineos-live-test-monitor");
                    retryMonitor.setDaemon(true);
                    retryMonitor.start();
                    return;
                } catch (IOException exception) {
                    logCapture("desktop fallback could not start: " + exception.getMessage());
                }
            }
            try {
                if (exitCode == 0 && Files.isRegularFile(video) && Files.size(video) > 0) {
                    Duration duration = Duration.between(startedAt, Instant.now());
                    Path sidecar = writeSidecar(video, Instant.now(), workspace, request, runId, duration, target);
                    result = RecordingResult.completed(video, sidecar, duration, target);
                } else {
                    result = RecordingResult.failed(
                            "Screen capture exited with code " + exitCode + " and did not produce an MP4.");
                }
            } catch (IOException exception) {
                result = RecordingResult.failed("Unable to write the live test Markdown sidecar: "
                        + exception.getMessage());
            } finally {
                callback = completion;
                clearState();
            }
        }
        if (callback != null) {
            callback.accept(result);
        }
    }

    private synchronized void clearState() {
        if (targetMonitor != null) {
            targetMonitor.cancel(false);
            targetMonitor = null;
        }
        captureCommand = null;
        captureStderrFile = null;
        fallbackAttempted = false;
        stopRequested = false;
        selectedWindow = null;
        process = null;
        video = null;
        workspace = null;
        request = null;
        runId = null;
        target = null;
        startedAt = null;
        completion = null;
    }

    private static void requireFfmpeg() {
        try {
            Process lookup = new ProcessBuilder("where.exe", "ffmpeg")
                    .redirectErrorStream(true)
                    .start();
            if (lookup.waitFor() != 0) {
                throw new IllegalStateException("ffmpeg was not found on PATH; install ffmpeg to record live tests.");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to locate ffmpeg on PATH.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while locating ffmpeg.", exception);
        }
    }

    static Path writeSidecar(
            Path video,
            Instant timestamp,
            Path workspace,
            String request,
            String runId,
            Duration duration,
            CaptureTarget target) throws IOException {
        Path sidecar = video.resolveSibling(video.getFileName().toString().replaceFirst("\\.mp4$", ".md"));
        String requestText = request == null || request.isBlank() ? "(none)" : request.trim();
        String runText = runId == null || runId.isBlank() ? "(none)" : runId;
        String markdown = """
                # EngineOS Live Test

                - Timestamp: %s
                - Workspace: `%s`
                - Run ID: `%s`
                - Video: `%s`
                - Recording: %s
                - Selected window: %s
                - Scope: %s
                - Capture note: %s
                - Duration: %d seconds

                ## Current Engineering Request

                ```
                %s
                ```

                Drop this video and markdown into an LLM chat for review.
                """.formatted(timestamp, workspace.toAbsolutePath().normalize(), runText, video,
                target.name(), target.windowTitle().orElse(target.name()), target.scope(),
                target.fallbackReason().isBlank() ? "(none)" : target.fallbackReason(),
                duration.toSeconds(), requestText);
        Files.writeString(sidecar, markdown);
        return sidecar;
    }

    static CaptureTarget targetFor(Path workspace) {
        String name = targetName(workspace);
        return new CaptureTarget(name, findWindowTitle(name));
    }

    static List<WindowInfo> runningWindows() {
        try {
            String output = runWindowScript(WINDOWS_SCRIPT);
            List<WindowInfo> windows = new ArrayList<>();
            var entries = new ObjectMapper().readTree(output);
            if (entries == null || !entries.isArray()) {
                throw new IOException("Window enumeration returned invalid JSON: " + output);
            }
            for (var entry : entries) {
                logWindowDiagnostic("discovered " + entry);
                windows.add(new WindowInfo(entry.get("Title").asText(), entry.get("Handle").asLong(),
                        entry.get("ProcessName").asText(), entry.get("ProcessId").asInt()));
            }
            int discoveredCount = windows.size();
            windows = windows.stream().filter(LiveTestRecorder::isUsefulWindow).toList();
            windows = new ArrayList<>(windows);
            windows.sort(Comparator.comparing(WindowInfo::title, String.CASE_INSENSITIVE_ORDER));
            logWindowCount(windows.size());
            logWindowDiagnostic("filtered out " + (discoveredCount - windows.size())
                    + " irrelevant windows");
            if (windows.isEmpty()) {
                logWindowDiagnostic("EnumWindows found no visible, titled top-level windows on this desktop.");
            }
            return windows;
        } catch (IOException exception) {
            logWindowDiagnostic("enumeration failed: " + exception.getMessage());
            throw new IllegalStateException("Unable to enumerate application windows", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logWindowDiagnostic("enumeration interrupted");
            throw new IllegalStateException("Window enumeration interrupted", exception);
        }
    }

    private static void logWindowDiagnostic(String message) {
        System.out.println("EngineOS live-test windows: " + message);
        DesktopDiagnostics.stage("live-test-windows " + message);
    }

    static boolean isUsefulWindow(WindowInfo window) {
        if (window == null || window.title() == null) {
            return false;
        }
        String title = window.title().trim();
        String titleLower = title.toLowerCase(java.util.Locale.ROOT);
        String processLower = window.processName() == null
                ? "" : window.processName().toLowerCase(java.util.Locale.ROOT);
        if (title.length() < 4
                || titleLower.equals("select window to record")
                || titleLower.equals("command palette")
                || titleLower.contains("command palette")
                || titleLower.contains("settings")
                || titleLower.contains("power toys quick access")
                || titleLower.contains("windows powershell")
                || titleLower.equals("program manager")) {
            return false;
        }
        if (Set.of("powershell", "pwsh", "cmd", "conhost", "windowsterminal",
                "systemsettings", "applicationframehost", "microsoft.cmdpal.ui",
                "powertoys.quickaccess", "searchhost", "shellexperiencehost")
                .contains(processLower)) {
            return false;
        }
        if (titleLower.contains("pom.xml")
                || titleLower.contains("[preview]")
                || titleLower.contains(" - microsoft visual studio")
                || titleLower.contains(" - intellij idea")
                || titleLower.contains(" - android studio")
                || titleLower.contains(" - visual studio code")) {
            return false;
        }
        return true;
    }

    private static String runWindowScript(String script) throws IOException, InterruptedException {
        String encoded = Base64.getEncoder().encodeToString(("""
                $ErrorActionPreference = 'Stop'
                $ProgressPreference = 'SilentlyContinue'
                [Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
                """ + script).getBytes(StandardCharsets.UTF_16LE));
        Process lookup = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                "-OutputFormat", "Text", "-EncodedCommand", encoded).redirectErrorStream(true).start();
        try {
            String output = new String(lookup.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\uFEFF", "").trim();
            int exit = lookup.waitFor();
            if (exit != 0) {
                throw new IOException("PowerShell exited " + exit + ": " + output);
            }
            return output;
        } finally {
            if (lookup.isAlive()) {
                lookup.destroy();
            }
        }
    }

    private static final String WINDOWS_SCRIPT = """
            Add-Type @'
            using System;
            using System.Text;
            using System.Diagnostics;
            using System.Collections.Generic;
            using System.ComponentModel;
            using System.Runtime.InteropServices;
            public static class EngineOsWindows {
              public delegate bool EnumWindowProc(IntPtr handle, IntPtr parameter);
              [DllImport("user32.dll", SetLastError=true)]
              public static extern bool EnumWindows(EnumWindowProc callback, IntPtr parameter);
              [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr handle);
              [DllImport("user32.dll", CharSet=CharSet.Unicode)]
              public static extern int GetWindowText(IntPtr handle, StringBuilder text, int length);
              [DllImport("user32.dll", CharSet=CharSet.Unicode)]
              public static extern int GetWindowTextLength(IntPtr handle);
              [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr handle, out uint id);
              public class WindowInfo {
                public string Title;
                public long Handle;
                public string ProcessName;
                public uint ProcessId;
              }
              public static WindowInfo[] List() {
                var windows = new List<WindowInfo>();
                if (!EnumWindows((handle, parameter) => {
                  if (!IsWindowVisible(handle)) return true;
                  var title = new StringBuilder(GetWindowTextLength(handle) + 1);
                  if (GetWindowText(handle, title, title.Capacity) == 0) return true;
                  uint id;
                  GetWindowThreadProcessId(handle, out id);
                  string name;
                  try {
                    using (var process = Process.GetProcessById((int)id)) name = process.ProcessName;
                  } catch (ArgumentException) {
                    name = "(process exited)";
                  } catch (Win32Exception error) {
                    name = "(unavailable: " + error.Message + ")";
                  } catch (InvalidOperationException error) {
                    name = "(unavailable: " + error.Message + ")";
                  }
                  windows.Add(new WindowInfo {
                    Title = title.ToString(), Handle = handle.ToInt64(), ProcessName = name, ProcessId = id
                  });
                  return true;
                }, IntPtr.Zero)) throw new Win32Exception(Marshal.GetLastWin32Error());
                return windows.ToArray();
              }
            }
            '@
            ConvertTo-Json -InputObject @([EngineOsWindows]::List()) -Compress
            """;

    private static void logWindowCount(int count) {
        System.out.println("EngineOS live-test windows found: " + count);
        DesktopDiagnostics.stage("live-test-windows-found-" + count);
    }

    static Optional<Rectangle> windowBounds(WindowInfo window) {
        String command = """
                [Console]::OutputEncoding = [Text.Encoding]::UTF8
                $OutputEncoding = [Text.Encoding]::UTF8
                Add-Type @'
                using System;
                using System.Runtime.InteropServices;
                public static class EngineOsWindow {
                  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);
                  public struct RECT { public int Left; public int Top; public int Right; public int Bottom; }
                }
                '@
                $r = New-Object EngineOsWindow+RECT
                if ([EngineOsWindow]::GetWindowRect([IntPtr]%d, [ref]$r)) {
                  "$($r.Left)|$($r.Top)|$($r.Right)|$($r.Bottom)"
                }
                """.formatted(window.handle());
        try {
            String[] parts = runWindowScript(command).split("\\|");
            if (parts.length == 4) {
                return Optional.of(new Rectangle(
                        Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]) - Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[3]) - Integer.parseInt(parts[1])));
            }
        } catch (IOException exception) {
            logWindowDiagnostic("bounds unavailable for " + window.handle() + ": " + exception.getMessage());
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    static Optional<String> targetLaunchDescription(Path workspace) {
        return launchCommand(workspace).map(LaunchCommand::description);
    }

    static boolean launchTarget(Path workspace) {
        Optional<LaunchCommand> command = launchCommand(workspace);
        if (command.isEmpty()) {
            return false;
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(command.get().command());
            builder.directory(workspace.toFile());
            builder.start();
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    static void openFolder(Path folder) {
        try {
            new ProcessBuilder("explorer.exe", folder.toAbsolutePath().normalize().toString()).start();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to open recording folder: " + folder, exception);
        }
    }

    static DeliveryResult deliverRecording(RecordingResult result) {
        return deliverRecording(result,
                paths -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(paths), null),
                LiveTestRecorder::openFolder);
    }

    static DeliveryResult deliverRecording(
            RecordingResult result, Consumer<String> clipboard, Consumer<Path> explorer) {
        String paths = result.video().toAbsolutePath().normalize() + System.lineSeparator()
                + result.sidecar().toAbsolutePath().normalize();
        boolean copied = false;
        boolean opened = false;
        String copyError = "";
        String folderError = "";
        for (int attempt = 0; attempt < 10 && !copied; attempt++) {
            try {
                clipboard.accept(paths);
                copied = true;
                copyError = "";
            } catch (IllegalStateException exception) {
                copyError = "Paths could not be copied: " + exception.getMessage();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    copyError = "Clipboard retry interrupted.";
                    break;
                }
            } catch (RuntimeException exception) {
                copyError = "Paths could not be copied: " + exception.getMessage();
                break;
            }
        }
        try {
            explorer.accept(result.video().toAbsolutePath().normalize().getParent());
            opened = true;
        } catch (RuntimeException exception) {
            folderError = "Folder could not be opened: " + exception.getMessage();
        }
        String error = (copyError + " " + folderError).trim();
        DesktopDiagnostics.stage("live-test-delivery copied=" + copied + " opened=" + opened
                + " video=" + result.video() + " sidecar=" + result.sidecar() + " " + error);
        return new DeliveryResult(copied, opened, error);
    }

    static String deliverySummary(RecordingResult result, DeliveryResult delivery) {
        String copyState = delivery.copied() ? "Paths copied to the clipboard." : "Paths could not be copied to the clipboard.";
        String folderState = delivery.opened() ? "Recording folder opened." : "Recording folder could not be opened.";
        return "Recording saved. " + folderState + " " + copyState;
    }

    record DeliveryResult(boolean copied, boolean opened, String error) {
    }

    static String targetName(Path workspace) {
        String leaf = workspace == null || workspace.getFileName() == null
                ? "Unknown application" : workspace.getFileName().toString();
        if ("engineos".equalsIgnoreCase(leaf)) {
            return "EngineOS";
        }
        String spaced = leaf.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
                .replaceAll("[_-]+", " ")
                .trim();
        return spaced.isBlank() ? "Unknown application"
                : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static Optional<String> findWindowTitle(String targetName) {
        String escapedName = targetName.replace("'", "''");
        String command = "$name='" + escapedName + "'; "
                + "Get-Process | Where-Object { $_.MainWindowTitle -like ('*' + $name + '*') } "
                + "| Select-Object -First 1 -ExpandProperty MainWindowTitle";
        try {
            Process lookup = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", command)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            String title = new String(lookup.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (lookup.waitFor() == 0 && !title.isBlank()) {
                return Optional.of(title);
            }
        } catch (IOException exception) {
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        return Optional.empty();
    }

    private static Optional<LaunchCommand> launchCommand(Path workspace) {
        if (workspace == null || !Files.isDirectory(workspace)) {
            return Optional.empty();
        }
        Path configured = workspace.resolve(".engineos").resolve("launch-command.txt");
        try {
            if (Files.isRegularFile(configured)) {
                String command = Files.readString(configured).trim();
                if (!command.isBlank()) {
                    return Optional.of(new LaunchCommand(List.of("cmd.exe", "/c", command),
                            "configured command (" + configured + ")"));
                }
            }
            Path commandFile = workspace.resolve(".engineos").resolve("launch.cmd");
            if (Files.isRegularFile(commandFile)) {
                return Optional.of(new LaunchCommand(List.of("cmd.exe", "/c", commandFile.toString()),
                        commandFile.toString()));
            }
            Path script = workspace.resolve(".engineos").resolve("launch.ps1");
            if (Files.isRegularFile(script)) {
                return Optional.of(new LaunchCommand(
                        List.of("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.toString()),
                        script.toString()));
            }
            try (var files = Files.list(workspace)) {
                return files.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".exe"))
                        .sorted().findFirst()
                        .map(path -> new LaunchCommand(List.of(path.toString()), path.toString()));
            }
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private static final String HOTKEY_SCRIPT = """
            $OutputEncoding = [Text.Encoding]::UTF8
            Add-Type @'
            using System;
            using System.Runtime.InteropServices;
            public static class EngineOsHotkey {
              [DllImport("user32.dll", SetLastError=true)]
              public static extern bool RegisterHotKey(IntPtr hWnd, int id, uint modifiers, uint key);
              [DllImport("user32.dll")] public static extern bool UnregisterHotKey(IntPtr hWnd, int id);
              [DllImport("user32.dll")] public static extern int GetMessage(out MSG message, IntPtr hWnd, uint min, uint max);
              [StructLayout(LayoutKind.Sequential)]
              public struct MSG {
                public IntPtr hWnd; public uint message; public UIntPtr wParam;
                public IntPtr lParam; public uint time; public int ptX; public int ptY;
              }
            }
            '@
            $registered = [EngineOsHotkey]::RegisterHotKey([IntPtr]::Zero, 1, 0x0002 -bor 0x0004, 0x52)
            if (-not $registered) {
              [Console]::WriteLine("ENGINEOS_HOTKEY|REGISTRATION_FAILED|" + [Runtime.InteropServices.Marshal]::GetLastWin32Error())
              exit 1
            }
            [Console]::WriteLine("ENGINEOS_HOTKEY|REGISTERED")
            [Console]::Out.Flush()
            $message = New-Object EngineOsHotkey+MSG
            while ([EngineOsHotkey]::GetMessage([ref]$message, [IntPtr]::Zero, 0, 0) -gt 0) {
              if ($message.message -eq 0x0312 -and $message.wParam.ToUInt64() -eq 1) {
                [Console]::WriteLine("ENGINEOS_HOTKEY|PRESSED")
                [Console]::Out.Flush()
              }
            }
            [EngineOsHotkey]::UnregisterHotKey([IntPtr]::Zero, 1)
            """;

    record WindowInfo(String title, long handle, String processName, int processId) {
        @Override
        public String toString() {
            return title + "  [" + processName + "]";
        }
    }

    private record LaunchCommand(List<String> command, String description) {
    }

    record CaptureTarget(String name, Optional<String> windowTitle, String fallbackReason) {
        CaptureTarget(String name, Optional<String> windowTitle) {
            this(name, windowTitle, "");
        }

        CaptureTarget {
            name = name == null || name.isBlank() ? "Unknown application" : name;
            windowTitle = windowTitle == null ? Optional.empty() : windowTitle;
            fallbackReason = fallbackReason == null ? "" : fallbackReason;
        }

        String scope() {
            return windowTitle.isPresent() ? "The running application window" : "Full primary desktop";
        }

        CaptureTarget desktopFallback(String reason) {
            return new CaptureTarget(name, Optional.empty(), reason);
        }
    }

    record RecordingResult(boolean succeeded, Path video, Path sidecar, Duration duration, CaptureTarget target,
                           String error) {
        static RecordingResult completed(Path video, Path sidecar, Duration duration, CaptureTarget target) {
            return new RecordingResult(true, video, sidecar, duration, target, "");
        }

        static RecordingResult failed(String error) {
            return new RecordingResult(false, null, null, Duration.ZERO, null, error);
        }
    }
}
