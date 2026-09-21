package com.engineos.application;

import com.engineos.ServiceHostApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceHostProcessTest {
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(15);

    @TempDir
    Path workspace;

    @Test
    void desktopClientUsesAnIndependentServiceProcess() throws Exception {
        Files.writeString(workspace.resolve("pom.xml"), "<project/>");
        int port = unusedPort();
        Process service = startService(port);
        try {
            URI serviceUri = URI.create("http://127.0.0.1:" + port);
            awaitService(serviceUri);

            EngineOsService firstDesktopClient = new HttpEngineOsService(serviceUri);
            assertTrue(firstDesktopClient.health().ready());
            assertEquals("github", firstDesktopClient.codingAgentProvider().providerId());
            assertEquals("copilot-cli", firstDesktopClient.codingAgentProvider().modelId());
            assertTrue(firstDesktopClient.verificationPlan(workspace, "ENGINEOS_REQUEST_V1").resolved());

            ProcessHandle serviceHandle = service.toHandle();
            assertTrue(serviceHandle.isAlive());
            assertNotEquals(ProcessHandle.current().pid(), serviceHandle.pid());

            EngineOsService restartedDesktopClient = new HttpEngineOsService(serviceUri);
            assertEquals("github", restartedDesktopClient.codingAgentProvider().providerId());
            assertTrue(serviceHandle.isAlive(),
                    "restarting the desktop client must not stop the service process");
        } finally {
            service.destroyForcibly();
            service.waitFor();
        }
    }

    private Process startService(int port) throws IOException {
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        return new ProcessBuilder(
                javaExecutable,
                "-Dengineos.service.port=" + port,
                "-cp",
                System.getProperty("java.class.path"),
                ServiceHostApplication.class.getName())
                .directory(workspace.toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    private static void awaitService(URI serviceUri) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(250))
                .build();
        HttpRequest request = HttpRequest.newBuilder(
                serviceUri.resolve("/api/engineos/provider"))
                .timeout(Duration.ofSeconds(1))
                .GET()
                .build();
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                var response = client.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (ConnectException | java.net.http.HttpTimeoutException ignored) {
                // The independently launched host may still be binding its port.
            }
            Thread.sleep(50);
        }
        throw new AssertionError("EngineOS service did not become ready at " + serviceUri);
    }

    private static int unusedPort() throws IOException {
        try (var socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
