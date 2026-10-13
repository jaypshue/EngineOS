package com.engineos.application;

import com.engineos.agent.AgentProvider;
import com.engineos.history.EngineeringHistoryRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicReference;
import com.engineos.infrastructure.git.GitSnapshot;

/**
 * Desktop-side transport client for the local EngineOS HTTP adapter.
 */
public final class HttpEngineOsService implements EngineOsService {
    private final URI baseUri;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final AtomicReference<String> activeRequest = new AtomicReference<>();

    public HttpEngineOsService(URI baseUri) {
        this.baseUri = baseUri;
        this.client = HttpClient.newHttpClient();
    }

    @Override
    public ServiceHealth health() {
        return get("/api/engineos/health", ServiceHealth.class);
    }

    @Override
    public AgentProvider codingAgentProvider() {
        return get("/api/engineos/provider", AgentProvider.class);
    }

    @Override
    public List<EngineeringHistoryRecord> historyRecords() {
        return get("/api/engineos/history", new TypeReference<>() {
        });
    }

    @Override
    public ServiceVerificationPlan verificationPlan(Path project, String prompt) {
        String path = "/api/engineos/verification-plan?project="
                + encode(project == null ? "" : project.toString())
                + "&prompt=" + encode(prompt == null ? "" : prompt);
        return get(path, ServiceVerificationPlan.class);
    }

    @Override
    public GitSnapshot sourceControl(Path project) {
        return get("/api/engineos/source-control?project="
                        + encode(project == null ? "" : project.toString()),
                GitSnapshot.class);
    }

    @Override
    public ExecutionResult submit(
            Path project,
            String prompt,
            String selectedModel,
            Consumer<ExecutionState> stateSink,
            Consumer<String> activitySink) {
        return submit(project, prompt, selectedModel, null, stateSink, activitySink);
    }

    @Override
    public ExecutionResult submit(
            Path project,
            String prompt,
            String selectedModel,
            GitSnapshot sourceControlBaseline,
            Consumer<ExecutionState> stateSink,
            Consumer<String> activitySink) {
        stateSink.accept(ExecutionState.STARTING);
        var request = new EngineOsHttpServer.SubmitRequest(
                project == null ? null : project.toString(), prompt, selectedModel,
                sourceControlBaseline);
        try {
            var httpRequest = HttpRequest.newBuilder(uri("/api/engineos/requests"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(request)))
                    .build();
            var response = client.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            ensureSuccess(response);
            var accepted = mapper.readValue(response.body(), EngineOsHttpServer.RequestAccepted.class);
            String requestId = accepted.requestId();
            activeRequest.set(requestId);
            ExecutionState lastState = ExecutionState.STARTING;
            int activityCount = 0;
            while (true) {
                var statusResponse = client.send(
                        HttpRequest.newBuilder(uri("/api/engineos/requests/" + requestId))
                                .GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                ensureSuccess(statusResponse);
                var status = mapper.readValue(statusResponse.body(), EngineOsHttpServer.RequestStatus.class);
                var activityResponse = client.send(
                        HttpRequest.newBuilder(uri("/api/engineos/requests/" + requestId + "/activity"))
                                .GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                ensureSuccess(activityResponse);
                var activity = mapper.readValue(
                        activityResponse.body(), EngineOsHttpServer.RequestActivity.class);
                while (activityCount < activity.activity().size()) {
                    activitySink.accept(activity.activity().get(activityCount++));
                }
                if (status.state() != lastState) {
                    lastState = status.state();
                    stateSink.accept(lastState);
                }
                if (status.complete()) {
                    var resultResponse = client.send(
                            HttpRequest.newBuilder(uri("/api/engineos/requests/" + requestId + "/result"))
                                    .GET().build(),
                            HttpResponse.BodyHandlers.ofByteArray());
                    ensureSuccess(resultResponse);
                    return mapper.readValue(resultResponse.body(), ExecutionResult.class);
                }
                Thread.sleep(150);
            }
        } catch (IOException exception) {
            throw requestFailure("/api/engineos/requests", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("EngineOS HTTP request interrupted", exception);
        } finally {
            activeRequest.set(null);
        }
    }

    @Override
    public boolean cancelActive() {
        String requestId = activeRequest.get();
        if (requestId == null) {
            return false;
        }
        try {
            var response = client.send(HttpRequest.newBuilder(
                            uri("/api/engineos/requests/" + requestId + "/cancel"))
                    .POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            ensureSuccess(response);
            return true;
        } catch (IOException exception) {
            throw requestFailure("/api/engineos/requests/" + requestId + "/cancel", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public boolean showActiveVerificationTarget() {
        return get("/api/engineos/active-target", new TypeReference<Map<String, Boolean>>() {
        }).getOrDefault("active", false);
    }

    @Override
    public com.engineos.infrastructure.git.GitCommitResult commitWorkspace(
            Path project, String runId, String message) {
        try {
            var request = new EngineOsHttpServer.CommitRequest(
                    project == null ? null : project.toString(), runId, message);
            var response = client.send(HttpRequest.newBuilder(uri("/api/engineos/commit"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(request)))
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            ensureSuccess(response);
            return mapper.readValue(response.body(),
                    com.engineos.infrastructure.git.GitCommitResult.class);
        } catch (IOException exception) {
            throw requestFailure("/api/engineos/commit", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("EngineOS HTTP commit interrupted", exception);
        }
    }

    private <T> T get(String path, Class<T> type) {
        return get(path, new TypeReference<>() {
        }, type);
    }

    private <T> T get(String path, TypeReference<T> type) {
        return get(path, type, null);
    }

    private <T> T get(String path, TypeReference<T> type, Class<T> rawType) {
        try {
            var response = client.send(HttpRequest.newBuilder(uri(path)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            ensureSuccess(response);
            return rawType == null
                    ? mapper.readValue(response.body(), type)
                    : mapper.readValue(response.body(), rawType);
        } catch (IOException exception) {
            throw requestFailure(path, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("EngineOS HTTP request interrupted", exception);
        }
    }

    private URI uri(String path) {
        return baseUri.resolve(path);
    }

    private IllegalStateException requestFailure(String path, IOException exception) {
        String detail = exception.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = exception.getClass().getSimpleName();
        }
        return new IllegalStateException(
                "EngineOS HTTP request failed for " + uri(path) + ": " + detail, exception);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void ensureSuccess(HttpResponse<?> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String detail = response.body() instanceof byte[] bytes
                    ? new String(bytes, StandardCharsets.UTF_8)
                    : String.valueOf(response.body());
            throw new IllegalStateException("EngineOS HTTP error " + response.statusCode()
                    + ": " + detail);
        }
    }
}
