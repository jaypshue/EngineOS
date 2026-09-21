package com.engineos.application;

import java.net.URI;

/**
 * Explicit local-process configuration shared by the Service and Desktop.
 */
public final class EngineOsServiceConfiguration {
    public static final String DEFAULT_BIND_ADDRESS = "127.0.0.1";
    public static final int DEFAULT_PORT = 8765;
    public static final String DEFAULT_SERVICE_URL =
            "http://" + DEFAULT_BIND_ADDRESS + ":" + DEFAULT_PORT;

    private EngineOsServiceConfiguration() {
    }

    public static ServiceBinding serviceBinding() {
        String address = configured(
                "engineos.service.bind-address",
                "ENGINEOS_SERVICE_BIND_ADDRESS",
                DEFAULT_BIND_ADDRESS);
        int port = configuredPort();
        if (address.isBlank()) {
            throw new IllegalStateException("EngineOS service bind address must not be blank.");
        }
        return new ServiceBinding(address, port);
    }

    public static URI desktopServiceUri() {
        String configured = configured(
                "engineos.service.url",
                "ENGINEOS_SERVICE_URL",
                DEFAULT_SERVICE_URL);
        try {
            URI uri = URI.create(configured);
            if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getPort() <= 0 || uri.getPath().contains(" ")) {
                throw new IllegalArgumentException("must be an HTTP URL with an explicit port");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Invalid EngineOS service URL '" + configured
                            + "'. Set ENGINEOS_SERVICE_URL or -Dengineos.service.url "
                            + "to an HTTP URL with an explicit port.",
                    exception);
        }
    }

    private static int configuredPort() {
        String configured = configured(
                "engineos.service.port",
                "ENGINEOS_SERVICE_PORT",
                String.valueOf(DEFAULT_PORT));
        try {
            int port = Integer.parseInt(configured);
            if (port < 1 || port > 65535) {
                throw new NumberFormatException("outside TCP port range");
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Invalid EngineOS service port '" + configured + "'.", exception);
        }
    }

    private static String configured(String property, String environment, String fallback) {
        return System.getProperty(property, System.getenv().getOrDefault(environment, fallback));
    }

    public record ServiceBinding(String bindAddress, int port) {
    }
}
