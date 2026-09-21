package com.engineos;

import com.engineos.application.EngineOsServiceHost;
import com.engineos.application.EngineOsServiceConfiguration;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/**
 * Standalone local EngineOS service host.
 *
 * <p>All workspace access, Copilot CLI execution, observation, verification,
 * and history persistence remain in this process.</p>
 */
public final class ServiceHostApplication {
    private ServiceHostApplication() {
    }

    public static void main(String[] args) throws InterruptedException {
        var binding = EngineOsServiceConfiguration.serviceBinding();
        Path workspaceRoot = Path.of(".").toAbsolutePath().normalize();
        var host = new EngineOsServiceHost(binding.bindAddress(), binding.port(), workspaceRoot);
        Runtime.getRuntime().addShutdownHook(new Thread(host::close, "engineos-service-shutdown"));
        host.start();
        System.out.println("EngineOS Service Host listening at " + host.baseUri());
        new CountDownLatch(1).await();
    }

}
