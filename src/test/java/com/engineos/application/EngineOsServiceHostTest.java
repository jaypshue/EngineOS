package com.engineos.application;

import com.engineos.agent.AgentProvider;
import com.engineos.observation.VerificationTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineOsServiceHostTest {
    @TempDir
    Path workspace;

    @Test
    void startsTheLocalExecutionCompositionOnAnIndependentHttpPort() throws Exception {
        Files.writeString(workspace.resolve("pom.xml"), "<project/>");
        var host = new EngineOsServiceHost(0, workspace);
        host.start();
        try {
            var client = new HttpEngineOsService(host.baseUri());
            assertTrue(client.health().ready());
            AgentProvider provider = client.codingAgentProvider();
            assertEquals("github", provider.providerId());
            assertEquals("copilot-cli", provider.modelId());
            var plan = client.verificationPlan(workspace, "ENGINEOS_REQUEST_V1");
            assertEquals(VerificationTarget.Kind.MAVEN, plan.workspaceKind());
            assertTrue(plan.resolved());
            assertTrue(HttpClient.newHttpClient()
                    .send(java.net.http.HttpRequest.newBuilder(host.baseUri()).GET().build(),
                            java.net.http.HttpResponse.BodyHandlers.discarding())
                    .statusCode() >= 400);
        } finally {
            host.close();
        }
    }
}
