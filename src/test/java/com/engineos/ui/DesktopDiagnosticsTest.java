package com.engineos.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DesktopDiagnosticsTest {
    @Test
    void appendsLifecycleStagesWithTimestampAndThread() throws Exception {
        Path log = Files.createTempFile("engineos-desktop-diagnostics", ".log");

        DesktopDiagnostics.stage(log, "done-entered");
        DesktopDiagnostics.stage(log, "done-completed");

        String content = Files.readString(log);
        assertTrue(content.contains("STAGE done-entered"));
        assertTrue(content.contains("STAGE done-completed"));
        assertTrue(content.contains("thread="));
        assertTrue(content.matches("(?s).*\\d{4}-\\d{2}-\\d{2}T.*"));
    }

    @Test
    void persistsExceptionTypeMessageAndStackTrace() throws Exception {
        Path log = Files.createTempFile("engineos-desktop-diagnostics", ".log");
        var failure = new IllegalStateException("diagnostic failure");

        DesktopDiagnostics.exception(log, "done", Thread.currentThread(), failure);

        String content = Files.readString(log);
        assertTrue(content.contains("EXCEPTION context=done"));
        assertTrue(content.contains("type=java.lang.IllegalStateException"));
        assertTrue(content.contains("message=diagnostic failure"));
        assertTrue(content.contains("DesktopDiagnosticsTest"));
        assertTrue(content.contains("IllegalStateException: diagnostic failure"));
    }
}
