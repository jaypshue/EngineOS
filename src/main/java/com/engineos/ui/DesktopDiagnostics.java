package com.engineos.ui;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Append-only diagnostics for Desktop lifecycle failures.
 */
public final class DesktopDiagnostics {
    private static final Object LOCK = new Object();
    private static final String LOG_FILE = "desktop-diagnostics.log";

    private DesktopDiagnostics() {
    }

    public static Path defaultLogPath() {
        String appData = System.getenv("APPDATA");
        Path base = appData == null || appData.isBlank()
                ? Path.of(System.getProperty("user.home"), "AppData", "Roaming")
                : Path.of(appData);
        return base.resolve("EngineOS").resolve(LOG_FILE);
    }

    public static void installUncaughtExceptionHandler() {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            exception("uncaught-exception", thread, throwable);
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        });
    }

    public static void stage(String stage) {
        write(defaultLogPath(), "STAGE " + stage);
    }

    public static void exception(String context, Throwable throwable) {
        exception(context, Thread.currentThread(), throwable);
    }

    static void stage(Path logPath, String stage) {
        write(logPath, "STAGE " + stage);
    }

    static void exception(Path logPath, String context, Thread thread, Throwable throwable) {
        write(logPath, formatException(context, thread, throwable));
    }

    private static void exception(String context, Thread thread, Throwable throwable) {
        write(defaultLogPath(), formatException(context, thread, throwable));
    }

    private static String formatException(String context, Thread thread, Throwable throwable) {
        var stack = new StringWriter();
        if (throwable != null) {
            throwable.printStackTrace(new PrintWriter(stack));
        }
        String message = throwable == null ? "" : String.valueOf(throwable.getMessage());
        String type = throwable == null ? "null" : throwable.getClass().getName();
        return "EXCEPTION context=" + context
                + " thread=" + thread.getName()
                + " type=" + type
                + " message=" + message
                + System.lineSeparator() + stack;
    }

    private static void write(Path logPath, String message) {
        String entry = Instant.now() + " thread=" + Thread.currentThread().getName()
                + " " + message + System.lineSeparator();
        synchronized (LOCK) {
            try {
                Files.createDirectories(logPath.toAbsolutePath().normalize().getParent());
                Files.writeString(logPath, entry, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND);
            } catch (IOException exception) {
                System.err.println("Unable to write EngineOS Desktop diagnostics: "
                        + exception.getMessage());
            }
        }
    }
}
