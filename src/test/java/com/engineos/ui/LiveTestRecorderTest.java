package com.engineos.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class LiveTestRecorderTest {
    @TempDir
    Path directory;

    @Test
    void retriesBusyClipboardAndOpensFolderIndependently() {
        var target = new LiveTestRecorder.CaptureTarget("EngineOS", Optional.of("EngineOS"));
        var result = LiveTestRecorder.RecordingResult.completed(
                directory.resolve("test.mp4"), directory.resolve("test.md"), Duration.ofSeconds(2), target);
        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<String> copied = new AtomicReference<>();
        AtomicReference<Path> opened = new AtomicReference<>();
        var delivery = LiveTestRecorder.deliverRecording(result, text -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("Clipboard busy");
            }
            copied.set(text);
        }, opened::set);
        assertTrue(delivery.copied() && delivery.opened());
        assertEquals(result.video().toAbsolutePath() + System.lineSeparator() + result.sidecar().toAbsolutePath(),
                copied.get());
        assertEquals(directory.toAbsolutePath(), opened.get());
        opened.set(null);
        var failedCopy = LiveTestRecorder.deliverRecording(result, text -> {
            throw new SecurityException("Clipboard unavailable");
        }, opened::set);
        assertFalse(failedCopy.copied());
        assertTrue(failedCopy.opened());
        assertEquals(directory.toAbsolutePath(), opened.get());
        var failedOpen = LiveTestRecorder.deliverRecording(result, copied::set, folder -> {
            throw new IllegalStateException("Explorer unavailable");
        });
        assertTrue(failedOpen.copied());
        assertFalse(failedOpen.opened());
        assertTrue(failedOpen.error().contains("Explorer unavailable"));
    }

    @Test
    void completionSummaryExplainsDeliveryAndListsArtifacts() {
        var target = new LiveTestRecorder.CaptureTarget("EngineOS", Optional.of("EngineOS"));
        var result = LiveTestRecorder.RecordingResult.completed(
                directory.resolve("test.mp4"), directory.resolve("test.md"), Duration.ofSeconds(2), target);

        String summary = LiveTestRecorder.deliverySummary(result,
                new LiveTestRecorder.DeliveryResult(true, true, ""));

        assertTrue(summary.contains("Recording saved."));
        assertTrue(summary.contains("Recording folder opened."));
        assertTrue(summary.contains("Paths copied to the clipboard."));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void recordsOddSizedWindowAndCanFallbackOnConsecutiveRecordings() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        org.junit.jupiter.api.Assumptions.assumeTrue(
                new ProcessBuilder("where.exe", "ffmpeg").start().waitFor() == 0);
        JFrame[] frame = new JFrame[1];
        LiveTestRecorder recorder = new LiveTestRecorder();
        var results = new LinkedBlockingQueue<LiveTestRecorder.RecordingResult>();
        String title = "EngineOS recorder regression " + System.nanoTime();
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame[0] = new JFrame(title);
                frame[0].setUndecorated(true);
                frame[0].setSize(321, 241);
                frame[0].setLocation(100, 100);
                frame[0].setVisible(true);
            });
            var window = LiveTestRecorder.runningWindows().stream()
                    .filter(item -> item.title().equals(title)).findFirst().orElseThrow();
            recorder.start(directory, "Verify odd bounds.", null,
                    new LiveTestRecorder.CaptureTarget("EngineOS", Optional.of(title)), window, results::add);
            Thread.sleep(2500);
            recorder.stop();
            var result = results.poll(15, TimeUnit.SECONDS);
            org.junit.jupiter.api.Assertions.assertNotNull(result);
            assertTrue(result.succeeded(), result.error());
            assertTrue(result.target().fallbackReason().isBlank(), result.target().fallbackReason());
            assertTrue(Files.size(result.video()) > 0);
            Process probe = new ProcessBuilder("ffprobe", "-v", "error", "-select_streams", "v:0",
                    "-show_entries", "stream=width,height", "-of", "csv=p=0", result.video().toString()).start();
            String dimensions = new String(probe.getInputStream().readAllBytes()).trim();
            String captureLog = Files.readString(result.video().resolveSibling(
                    result.video().getFileName() + ".window.ffmpeg.log"));
            var inputSize = java.util.regex.Pattern.compile("capturing (\\d+)x(\\d+)").matcher(captureLog);
            assertTrue(inputSize.find(), captureLog);
            int width = Integer.parseInt(inputSize.group(1));
            int height = Integer.parseInt(inputSize.group(2));
            assertTrue(width % 2 != 0 || height % 2 != 0, "Test window must exercise odd physical bounds");
            assertEquals((width + width % 2) + "," + (height + height % 2), dimensions);
            assertEquals(0, probe.waitFor());
            recorder.start(directory, "Stop when the window closes.", null,
                    new LiveTestRecorder.CaptureTarget("EngineOS", Optional.of(title)), window, results::add);
            Thread.sleep(2000);
            SwingUtilities.invokeAndWait(() -> frame[0].dispose());
            var closed = results.poll(15, TimeUnit.SECONDS);
            org.junit.jupiter.api.Assertions.assertNotNull(closed, "Closing the target must finish recording");
            assertTrue(closed.succeeded(), closed.error());
            assertTrue(closed.target().fallbackReason().isBlank(), "Closing the target must not start desktop capture");
            for (int attempt = 0; attempt < 2; attempt++) {
                recorder.start(directory, "Fallback regression", null,
                        new LiveTestRecorder.CaptureTarget("EngineOS", Optional.of("missing-" + title)), results::add);
                Thread.sleep(2500);
                recorder.stop();
                var fallback = results.poll(15, TimeUnit.SECONDS);
                org.junit.jupiter.api.Assertions.assertNotNull(fallback);
                assertTrue(fallback.succeeded(), fallback.error());
                assertFalse(fallback.target().fallbackReason().isBlank());
                assertTrue(Files.readString(fallback.sidecar()).contains("Full primary desktop"));
                assertTrue(Files.size(fallback.video()) > 0);
                String stderr = Files.readString(fallback.video().resolveSibling(
                        fallback.video().getFileName() + ".window.ffmpeg.log"));
                assertTrue(stderr.contains("missing-" + title));
            }
        } finally {
            recorder.stop();
            SwingUtilities.invokeAndWait(() -> {
                if (frame[0] != null) frame[0].dispose();
            });
        }
    }

    @Test
    void sidecarContainsLiveTestContext() throws Exception {
        Path video = directory.resolve("live-test.mp4");
        Path sidecar = LiveTestRecorder.writeSidecar(video, Instant.parse("2026-11-11T21:00:00Z"),
                directory, "Investigate the live path.", "run-123", Duration.ofSeconds(17),
                new LiveTestRecorder.CaptureTarget("Interview Assistant", java.util.Optional.empty()));

        String markdown = Files.readString(sidecar);
        assertTrue(markdown.contains("Timestamp: 2026-11-11T21:00:00Z"));
        assertTrue(markdown.contains("Workspace: `" + directory.toAbsolutePath().normalize()));
        assertTrue(markdown.contains("Investigate the live path."));
        assertTrue(markdown.contains("Run ID: `run-123`"));
        assertTrue(markdown.contains("Video: `" + video + "`"));
        assertTrue(markdown.contains("Recording: Interview Assistant"));
        assertTrue(markdown.contains("Scope: Full primary desktop"));
        assertTrue(markdown.contains("Duration: 17 seconds"));
        assertTrue(markdown.contains("Drop this video and markdown into an LLM chat for review."));
    }

    @Test
    void targetNameUsesWorkspaceName() {
        assertTrue("Interview Assistant".equals(
                LiveTestRecorder.targetName(Path.of("C:\\projects\\InterviewAssistant"))));
        assertTrue("EngineOS".equals(
                LiveTestRecorder.targetName(Path.of("C:\\projects\\EngineOS"))));
    }

    @Test
    void filtersToolWindowsAndKeepsUsefulApplications() {
        assertTrue(LiveTestRecorder.isUsefulWindow(
                new LiveTestRecorder.WindowInfo("EngineOS", 1, "java", 1)));
        assertTrue(LiveTestRecorder.isUsefulWindow(
                new LiveTestRecorder.WindowInfo("Interview Assistant", 2, "InterviewAssistant", 2)));
        assertTrue(LiveTestRecorder.isUsefulWindow(
                new LiveTestRecorder.WindowInfo("Example - Google Chrome", 3, "chrome", 3)));
        assertFalse(LiveTestRecorder.isUsefulWindow(
                new LiveTestRecorder.WindowInfo("Command Palette", 4, "Microsoft.CmdPal.UI", 4)));
        assertFalse(LiveTestRecorder.isUsefulWindow(
                new LiveTestRecorder.WindowInfo("EngineOS - pom.xml", 5, "idea64", 5)));
        assertFalse(LiveTestRecorder.isUsefulWindow(
                new LiveTestRecorder.WindowInfo(
                        "InterviewCubed - plan.md [Preview] - Microsoft Visual Studio",
                        6, "devenv", 6)));
        assertFalse(LiveTestRecorder.isUsefulWindow(
                new LiveTestRecorder.WindowInfo("Settings", 7, "SystemSettings", 7)));
        assertFalse(LiveTestRecorder.isUsefulWindow(
                new LiveTestRecorder.WindowInfo("abc", 8, "java", 8)));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void enumeratesVisibleEngineOsAndOtherWindowsInSameProcess() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        JFrame[] frames = new JFrame[3];
        String otherTitle = "Recorder test | \"Second\" \u00e9";
        try {
            SwingUtilities.invokeAndWait(() -> {
                frames[0] = new JFrame("EngineOS");
                frames[1] = new JFrame(otherTitle);
                frames[2] = new JFrame("Hidden recorder test");
                for (int index = 0; index < frames.length; index++) {
                    frames[index].setSize(240, 100);
                    frames[index].setLocation(80 + index * 260, 100);
                    frames[index].setVisible(index < 2);
                }
                frames[0].toFront();
            });
            var windows = LiveTestRecorder.runningWindows();
            long processId = ProcessHandle.current().pid();
            var ownWindows = windows.stream().filter(window -> window.processId() == processId).toList();
            var engineOs = ownWindows.stream().filter(window -> window.title().equals("EngineOS"))
                    .findFirst().orElseThrow();
            assertTrue(engineOs.handle() != 0);
            assertFalse(engineOs.processName().isBlank());
            assertTrue(ownWindows.stream().anyMatch(window -> window.title().equals(otherTitle)));
            assertFalse(ownWindows.stream().anyMatch(window -> window.title().equals("Hidden recorder test")));
            var bounds = LiveTestRecorder.windowBounds(engineOs).orElseThrow();
            assertTrue(bounds.width > 0 && bounds.height > 0);
            var refreshed = LiveTestRecorder.runningWindows();
            assertEquals(1, refreshed.stream().filter(window -> window.handle() == engineOs.handle()).count());
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                for (JFrame frame : frames) {
                    if (frame != null) {
                        frame.dispose();
                    }
                }
            });
        }
    }
}
