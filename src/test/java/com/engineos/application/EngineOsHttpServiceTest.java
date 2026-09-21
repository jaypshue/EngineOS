package com.engineos.application;

import com.engineos.agent.AgentProvider;
import com.engineos.history.EngineeringHistoryRecord;
import com.engineos.observation.ObservationRunner;
import com.engineos.observation.VerificationTarget;
import com.engineos.domain.EngineeringResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineOsHttpServiceTest {
    @TempDir
    Path project;

    @Test
    void translatesServiceOperationsAcrossLocalhostHttp() {
        var service = new RecordingService();
        var server = new EngineOsHttpServer(service);
        server.start();
        try {
            EngineOsService client = new HttpEngineOsService(server.baseUri());
            assertTrue(client.health().ready());
            assertEquals("gpt-5.6-luna", client.codingAgentProvider().modelId());
            assertTrue(client.historyRecords().isEmpty());

            var plan = client.verificationPlan(project, "ENGINEOS_REQUEST_V1");
            assertEquals(project, plan.workspaceRoot());
            assertEquals(VerificationTarget.Kind.ENGINEOS, plan.targetKind());
            assertTrue(plan.resolved());

            var states = new ArrayList<EngineOsService.ExecutionState>();
            var result = client.submit(project, "ENGINEOS_REQUEST_V1", "gpt-5.6-luna",
                    states::add, activity -> states.add(EngineOsService.ExecutionState.CODING_AGENT_WORKING));

            assertEquals(EngineOsService.ExecutionState.GREEN, result.state());
            assertEquals("ok", result.detail());
            assertEquals(new ObservationRunner.ObservationResult(0, "observed", ""), result.observation());
            assertEquals(List.of(
                    EngineOsService.ExecutionState.STARTING,
                    EngineOsService.ExecutionState.CODING_AGENT_WORKING,
                    EngineOsService.ExecutionState.GREEN), states);
            assertFalse(client.showActiveVerificationTarget());
            assertEquals(1, service.submissions.get());
        } finally {
            server.close();
        }
    }

    @Test
    void reportsTheServiceEndpointWhenTheHostIsUnavailable() throws Exception {
        int port;
        try (var socket = new java.net.ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        var service = new HttpEngineOsService(
                java.net.URI.create("http://127.0.0.1:" + port));

        var failure = assertThrows(IllegalStateException.class, service::codingAgentProvider);

        assertTrue(failure.getMessage().contains("/api/engineos/provider"));
        assertTrue(failure.getMessage().contains("127.0.0.1:" + port));
        assertTrue(failure.getMessage().contains("ConnectException"));
    }

    @Test
    void forwardsProgressBeforeTheLongRunningRequestCompletes() throws Exception {
        var service = new DelayedRecordingService();
        var server = new EngineOsHttpServer(service);
        server.start();
        var executor = Executors.newSingleThreadExecutor();
        try {
            EngineOsService client = new HttpEngineOsService(server.baseUri());
            var states = new CopyOnWriteArrayList<EngineOsService.ExecutionState>();
            var activities = new CopyOnWriteArrayList<String>();
            var future = executor.submit(() -> client.submit(
                    project, "ENGINEOS_REQUEST_V1", "gpt-5.6-luna",
                    states::add, activities::add));

            assertTrue(service.started.await(2, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while ((!states.contains(EngineOsService.ExecutionState.CODING_AGENT_WORKING)
                    || !activities.contains("working"))
                    && System.nanoTime() < deadline) {
                Thread.sleep(25);
            }

            assertTrue(states.contains(EngineOsService.ExecutionState.CODING_AGENT_WORKING));
            assertTrue(activities.contains("working"));
            assertFalse(future.isDone());

            service.release.countDown();
            assertEquals(EngineOsService.ExecutionState.GREEN, future.get(2, TimeUnit.SECONDS).state());
        } finally {
            service.release.countDown();
            executor.shutdownNow();
            server.close();
        }
    }

    @Test
    void cancelsOwnedHttpRequestAndKeepsServiceUsableForNextRequest() throws Exception {
        var service = new ControlledCancellationService();
        var server = new EngineOsHttpServer(service);
        server.start();
        var executor = Executors.newSingleThreadExecutor();
        try {
            var client = new HttpEngineOsService(server.baseUri());
            var first = executor.submit(() -> client.submit(
                    project, "first", "test", state -> { }, activity -> { }));

            assertTrue(service.started.await(2, TimeUnit.SECONDS));
            boolean cancellationSent = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (!cancellationSent && System.nanoTime() < deadline) {
                cancellationSent = client.cancelActive();
                if (!cancellationSent) {
                    Thread.sleep(10);
                }
            }
            assertTrue(cancellationSent);
            var cancelled = first.get(2, TimeUnit.SECONDS);
            assertEquals(EngineOsService.ExecutionState.CANCELLED, cancelled.state());
            assertEquals(1, service.cancellations.get());
            assertEquals("NOT_RUN", cancelled.engineeringResult().verification().status());
            assertFalse(cancelled.engineeringResult().verification().succeeded());

            var subsequent = client.submit(
                    project, "second", "test", state -> { }, activity -> { });
            assertEquals(EngineOsService.ExecutionState.GREEN, subsequent.state());
            assertEquals(2, service.submissions.get());
        } finally {
            service.release.countDown();
            executor.shutdownNow();
            server.close();
        }
    }

    @Test
    void convertsInterruptedCancelledExecutionIntoExplicitCancelledResult() throws Exception {
        var service = new InterruptingCancellationService();
        var server = new EngineOsHttpServer(service);
        server.start();
        var executor = Executors.newSingleThreadExecutor();
        try {
            var client = new HttpEngineOsService(server.baseUri());
            var request = executor.submit(() -> client.submit(
                    project, "cancel me", "test", state -> { }, activity -> { }));

            assertTrue(service.started.await(2, TimeUnit.SECONDS));
            boolean cancellationSent = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (!cancellationSent && System.nanoTime() < deadline) {
                cancellationSent = client.cancelActive();
                if (!cancellationSent) {
                    Thread.sleep(10);
                }
            }

            assertTrue(cancellationSent);
            var cancelled = request.get(2, TimeUnit.SECONDS);
            assertEquals(EngineOsService.ExecutionState.CANCELLED, cancelled.state());
            assertEquals("CANCELLED - Request cancelled.", cancelled.detail());
        } finally {
            executor.shutdownNow();
            server.close();
        }
    }

    @Test
    void exposesSeparateStatusActivityAndResultEndpoints() throws Exception {
        var server = new EngineOsHttpServer(new RecordingService());
        server.start();
        try {
            var http = HttpClient.newHttpClient();
            var mapper = new ObjectMapper().findAndRegisterModules();
            var body = mapper.writeValueAsBytes(
                    new EngineOsHttpServer.SubmitRequest(project.toString(), "ENGINEOS_REQUEST_V1", "test"));
            var acceptedResponse = http.send(
                    HttpRequest.newBuilder(server.baseUri().resolve("/api/engineos/requests"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(202, acceptedResponse.statusCode());
            String requestId = mapper.readValue(
                    acceptedResponse.body(), EngineOsHttpServer.RequestAccepted.class).requestId();

            assertEquals(200, http.send(HttpRequest.newBuilder(server.baseUri().resolve(
                    "/api/engineos/requests/" + requestId)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray()).statusCode());
            assertEquals(200, http.send(HttpRequest.newBuilder(server.baseUri().resolve(
                    "/api/engineos/requests/" + requestId + "/activity")).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray()).statusCode());
            assertEquals(200, http.send(HttpRequest.newBuilder(server.baseUri().resolve(
                    "/api/engineos/requests/" + requestId + "/result")).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray()).statusCode());
            assertEquals(404, http.send(HttpRequest.newBuilder(server.baseUri().resolve(
                    "/api/engineos/requests/unknown")).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray()).statusCode());
        } finally {
            server.close();
        }
    }

    private class RecordingService implements EngineOsService {
        protected final AtomicInteger submissions = new AtomicInteger();

        @Override
        public ServiceHealth health() {
            return new ServiceHealth(true, "test");
        }

        @Override
        public AgentProvider codingAgentProvider() {
            return new AgentProvider("github", "gpt-5.6-luna");
        }

        @Override
        public List<EngineeringHistoryRecord> historyRecords() {
            return List.of();
        }

        @Override
        public ServiceVerificationPlan verificationPlan(Path project, String prompt) {
            return new ServiceVerificationPlan(project, VerificationTarget.Kind.ENGINEOS,
                    VerificationTarget.Kind.ENGINEOS, false, false, "");
        }

        @Override
        public ExecutionResult submit(
                Path project,
                String prompt,
                String selectedModel,
                java.util.function.Consumer<ExecutionState> stateSink,
                java.util.function.Consumer<String> activitySink) {
            submissions.incrementAndGet();
            stateSink.accept(ExecutionState.GREEN);
            activitySink.accept("activity");
            return new ExecutionResult(ExecutionState.GREEN, "ok", null,
                    new ObservationRunner.ObservationResult(0, "observed", ""),
                    List.of("activity"), null);
        }

        @Override
        public boolean showActiveVerificationTarget() {
            return false;
        }
    }

    private final class DelayedRecordingService extends RecordingService {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public ExecutionResult submit(
                Path project,
                String prompt,
                String selectedModel,
                java.util.function.Consumer<ExecutionState> stateSink,
                java.util.function.Consumer<String> activitySink) {
            stateSink.accept(ExecutionState.CODING_AGENT_WORKING);
            activitySink.accept("working");
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", exception);
            }
            stateSink.accept(ExecutionState.GREEN);
            return new ExecutionResult(ExecutionState.GREEN, "ok", null,
                    new ObservationRunner.ObservationResult(0, "observed", ""),
                    List.of("working"), null);
        }

    }

    private final class ControlledCancellationService extends RecordingService {
            private final CountDownLatch started = new CountDownLatch(1);
            private final CountDownLatch release = new CountDownLatch(1);
            private final AtomicInteger cancellations = new AtomicInteger();

            @Override
            public ExecutionResult submit(
                    Path project,
                    String prompt,
                    String selectedModel,
                    java.util.function.Consumer<ExecutionState> stateSink,
                    java.util.function.Consumer<String> activitySink) {
                int request = submissions.incrementAndGet();
                if (request > 1) {
                    stateSink.accept(ExecutionState.GREEN);
                    return new ExecutionResult(ExecutionState.GREEN, "ok", null,
                            new ObservationRunner.ObservationResult(0, "observed", ""),
                            List.of("second"), null);
                }
                stateSink.accept(ExecutionState.CODING_AGENT_WORKING);
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                return new ExecutionResult(ExecutionState.CANCELLED,
                        "CANCELLED - Implementation not complete", null, null,
                        List.of("started"), new EngineeringResult(
                                prompt, null, List.of("started"), null,
                                new EngineeringResult.Verification("NOT_RUN", null, null, false),
                                new EngineeringResult.Observation("NOT_RUN", null, null, false, null),
                                null, new EngineeringResult.Supervision(
                                        "CANCELLED", false, List.of("Verification did not run."), "CANCELLED")));
            }

            @Override
            public boolean cancelActive() {
                cancellations.incrementAndGet();
                release.countDown();
                return true;
        }
    }

    private final class InterruptingCancellationService extends RecordingService {
        private final CountDownLatch started = new CountDownLatch(1);

        @Override
        public ExecutionResult submit(
                Path project,
                String prompt,
                String selectedModel,
                java.util.function.Consumer<ExecutionState> stateSink,
                java.util.function.Consumer<String> activitySink) {
            stateSink.accept(ExecutionState.CODING_AGENT_WORKING);
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", exception);
            }
            return new ExecutionResult(ExecutionState.GREEN, "unexpected", null, null, List.of(), null);
        }
    }
}
