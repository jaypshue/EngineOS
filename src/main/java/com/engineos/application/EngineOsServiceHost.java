package com.engineos.application;

import com.engineos.agent.AgentProvider;
import com.engineos.agent.CopilotCoderAgent;
import com.engineos.agent.CopilotProposalCompletion;
import com.engineos.agent.LlmEngineerAgent;
import com.engineos.history.FileEngineeringHistoryRepository;
import com.engineos.observation.PowerShellObservationRunner;

import java.net.URI;
import java.nio.file.Path;

/**
 * Runtime composition for the independently launched local service process.
 */
public final class EngineOsServiceHost implements AutoCloseable {
    private final EngineOsHttpServer server;

    public EngineOsServiceHost(int port, Path workspaceRoot) {
        this(EngineOsServiceConfiguration.DEFAULT_BIND_ADDRESS, port, workspaceRoot);
    }

    public EngineOsServiceHost(String bindAddress, int port, Path workspaceRoot) {
        var service = new LocalEngineOsService(
                new EngineeringSessionRunner(
                        new LlmEngineerAgent(new AgentProvider("github", "auto"),
                                new CopilotProposalCompletion("auto")),
                        new CopilotCoderAgent(new AgentProvider("github", "copilot-cli")),
                        new PowerShellObservationRunner(workspaceRoot),
                        new FileEngineeringHistoryRepository()));
        server = new EngineOsHttpServer(service, bindAddress, port);
    }

    public void start() {
        server.start();
    }

    public URI baseUri() {
        return server.baseUri();
    }

    @Override
    public void close() {
        server.close();
    }
}
