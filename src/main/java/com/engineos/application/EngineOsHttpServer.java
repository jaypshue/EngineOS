package com.engineos.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thin localhost transport adapter for the EngineOS application boundary.
 */
public final class EngineOsHttpServer implements AutoCloseable {
    private final EngineOsService service;
    private final HttpServer server;
    private final ExecutorService executor;
    private final ExecutorService executionExecutor;
    private final Map<String, RequestJob> jobs = new ConcurrentHashMap<>();
    private final String bindAddress;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public EngineOsHttpServer(EngineOsService service) {
        this(service, 0);
    }

    public EngineOsHttpServer(EngineOsService service, int port) {
        this(service, EngineOsServiceConfiguration.DEFAULT_BIND_ADDRESS, port);
    }

    public EngineOsHttpServer(EngineOsService service, String bindAddress, int port) {
        this.service = service;
        this.bindAddress = bindAddress;
        try {
            server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create EngineOS localhost server", exception);
        }
        server.createContext("/api/engineos/provider", this::provider);
        server.createContext("/api/engineos/health", this::health);
        server.createContext("/api/engineos/history", this::history);
        server.createContext("/api/engineos/verification-plan", this::verificationPlan);
        server.createContext("/api/engineos/active-target", this::activeTarget);
        server.createContext("/api/engineos/requests", this::request);
        executor = Executors.newCachedThreadPool(runnable -> {
            var thread = new Thread(runnable, "engineos-service-http");
            thread.setDaemon(true);
            return thread;
        });
        executionExecutor = Executors.newCachedThreadPool(runnable -> {
            var thread = new Thread(runnable, "engineos-service-execution");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
    }

    public void start() {
        server.start();
    }

    public URI baseUri() {
        String advertisedAddress = bindAddress.equals("0.0.0.0") ? "127.0.0.1" : bindAddress;
        return URI.create("http://" + advertisedAddress + ":" + server.getAddress().getPort());
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
        executionExecutor.shutdownNow();
    }

    private void provider(HttpExchange exchange) throws IOException {
        if (!get(exchange)) {
            return;
        }
        write(exchange, 200, service.codingAgentProvider());
    }

    private void health(HttpExchange exchange) throws IOException {
        if (!get(exchange)) {
            return;
        }
        write(exchange, 200, service.health());
    }

    private void history(HttpExchange exchange) throws IOException {
        if (!get(exchange)) {
            return;
        }
        write(exchange, 200, service.historyRecords());
    }

    private void verificationPlan(HttpExchange exchange) throws IOException {
        if (!get(exchange)) {
            return;
        }
        var query = query(exchange.getRequestURI());
        var project = query.get("project");
        var prompt = query.get("prompt");
        write(exchange, 200, service.verificationPlan(
                project == null ? null : java.nio.file.Path.of(project), prompt));
    }

    private void activeTarget(HttpExchange exchange) throws IOException {
        if (!get(exchange)) {
            return;
        }
        write(exchange, 200, Map.of("active", service.showActiveVerificationTarget()));
    }

    private void request(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String prefix = "/api/engineos/requests/";
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod()) && path.startsWith(prefix)) {
            String suffix = path.substring(prefix.length());
            if (suffix.endsWith("/activity")) {
                requestActivity(exchange, suffix.substring(0, suffix.length() - "/activity".length()));
            } else if (suffix.endsWith("/result")) {
                requestResult(exchange, suffix.substring(0, suffix.length() - "/result".length()));
            } else {
                requestStatus(exchange, suffix);
            }
            return;
        }
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) && path.startsWith(prefix)
                && path.endsWith("/cancel")) {
            String requestId = path.substring(prefix.length(),
                    path.length() - "/cancel".length());
            RequestJob job = jobs.get(requestId);
            if (job == null) {
                write(exchange, 404, Map.of("error", "Unknown EngineOS request: " + requestId));
            } else {
                write(exchange, job.cancel(service::cancelActive) ? 202 : 409,
                        Map.of("cancelled", job.cancelled()));
            }
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod()) || !path.equals("/api/engineos/requests")) {
            write(exchange, 405, Map.of("error", "POST is required for a new request or GET is required for a request status"));
            return;
        }
        try {
            var body = mapper.readValue(exchange.getRequestBody(), SubmitRequest.class);
            String requestId = UUID.randomUUID().toString();
            var job = new RequestJob(requestId);
            jobs.put(requestId, job);
            executionExecutor.submit(() -> execute(job, body));
            write(exchange, 202, new RequestAccepted(requestId));
        } catch (RuntimeException exception) {
            write(exchange, 400, Map.of("error",
                    exception.getMessage() == null ? "Invalid EngineOS request" : exception.getMessage()));
        }
    }

    private void execute(RequestJob job, SubmitRequest body) {
        job.executionThread = Thread.currentThread();
        try {
            var result = service.submit(
                    body.project() == null ? null : java.nio.file.Path.of(body.project()),
                    body.prompt(),
                    body.selectedModel(),
                    job::state,
                    job::activity);
            job.complete(job.cancelled() && result.state() != EngineOsService.ExecutionState.CANCELLED
                    ? cancelledResult(job) : result);
        } catch (RuntimeException exception) {
            job.complete(job.cancelled()
                    ? cancelledResult(job)
                    : new EngineOsService.ExecutionResult(
                            EngineOsService.ExecutionState.FAILED,
                            "FAILED - " + (exception.getMessage() == null
                                    ? "EngineOS request failed"
                                    : exception.getMessage()),
                            null, null, job.activities(), null));
        }
    }

    private static EngineOsService.ExecutionResult cancelledResult(RequestJob job) {
        return new EngineOsService.ExecutionResult(
                EngineOsService.ExecutionState.CANCELLED,
                "CANCELLED - Request cancelled.",
                null, null, job.activities(), null);
    }

    private void requestStatus(HttpExchange exchange, String requestId) throws IOException {
        RequestJob job = jobs.get(requestId);
        if (job == null) {
            write(exchange, 404, Map.of("error", "Unknown EngineOS request: " + requestId));
            return;
        }
        write(exchange, 200, job.status());
    }

    private void requestActivity(HttpExchange exchange, String requestId) throws IOException {
        RequestJob job = jobs.get(requestId);
        if (job == null) {
            write(exchange, 404, Map.of("error", "Unknown EngineOS request: " + requestId));
            return;
        }
        write(exchange, 200, job.activity());
    }

    private void requestResult(HttpExchange exchange, String requestId) throws IOException {
        RequestJob job = jobs.get(requestId);
        if (job == null) {
            write(exchange, 404, Map.of("error", "Unknown EngineOS request: " + requestId));
            return;
        }
        if (!job.complete()) {
            write(exchange, 409, Map.of("error", "EngineOS request has not completed: " + requestId));
            return;
        }
        write(exchange, 200, job.result());
    }

    private boolean get(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            write(exchange, 405, Map.of("error", "GET is required"));
            return false;
        }
        return true;
    }

    private void write(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] body = mapper.writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static Map<String, String> query(URI uri) {
        if (uri.getRawQuery() == null || uri.getRawQuery().isBlank()) {
            return Map.of();
        }
        return java.util.Arrays.stream(uri.getRawQuery().split("&"))
                .map(pair -> pair.split("=", 2))
                .filter(pair -> pair.length == 2)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        pair -> decode(pair[0]), pair -> decode(pair[1]), (first, second) -> second));
    }

    private static String decode(String value) {
        return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    public record SubmitRequest(String project, String prompt, String selectedModel) {
    }

    public record RequestAccepted(String requestId) {
    }

    public record RequestStatus(
            String requestId,
            EngineOsService.ExecutionState state,
            boolean complete) {
    }

    public record RequestActivity(String requestId, List<String> activity) {
    }

    private static final class RequestJob {
        private final String requestId;
        private final List<String> activity = new java.util.ArrayList<>();
        private volatile EngineOsService.ExecutionState state = EngineOsService.ExecutionState.STARTING;
        private volatile EngineOsService.ExecutionResult result;
        private volatile Thread executionThread;
        private volatile boolean cancelled;

        private RequestJob(String requestId) {
            this.requestId = requestId;
        }

        private void state(EngineOsService.ExecutionState state) {
            this.state = state;
        }

        private synchronized void activity(String entry) {
            activity.add(entry);
        }

        private void complete(EngineOsService.ExecutionResult result) {
            this.result = result;
            this.state = result.state();
        }

        private boolean cancel(java.util.function.BooleanSupplier serviceCancellation) {
            if (result != null) {
                return false;
            }
            cancelled = true;
            serviceCancellation.getAsBoolean();
            Thread thread = executionThread;
            if (thread != null) {
                thread.interrupt();
            }
            return true;
        }

        private boolean cancelled() {
            return cancelled;
        }

        private synchronized RequestStatus status() {
            return new RequestStatus(requestId, state, result != null);
        }

        private synchronized RequestActivity activity() {
            return new RequestActivity(requestId, List.copyOf(activity));
        }

        private synchronized List<String> activities() {
            return List.copyOf(activity);
        }

        private boolean complete() {
            return result != null;
        }

        private EngineOsService.ExecutionResult result() {
            return result;
        }
    }
}
