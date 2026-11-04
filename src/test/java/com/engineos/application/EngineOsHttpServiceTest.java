package com.engineos.application;

import com.engineos.agent.AgentProvider;
import com.engineos.domain.EngineeringTask;
import com.engineos.history.EngineeringHistoryRecord;
import com.engineos.observation.ObservationRunner;
import com.engineos.observation.VerificationTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineOsHttpServiceTest {
    @TempDir
    Path project;

    @Test
    void proposalWaitsForExplicitApprovalBeforeExecution() {
        var service = new RecordingService();
        var server = new EngineOsHttpServer(service);
        server.start();
        try {
            EngineOsService client = new HttpEngineOsService(server.baseUri());
            assertTrue(client.health().ready());
            assertEquals("gpt-5.6-luna", client.codingAgentProvider().modelId());
            assertTrue(client.historyRecords().isEmpty());

            var states = new ArrayList<EngineOsService.ExecutionState>();
            var activities = new ArrayList<String>();
            var proposal = client.propose("desktop-id", project, """
                    ENGINEOS_REQUEST_V1
                    Goal:
                    Establish the requested change.
                    Task:
                    Modify the selected workspace.
                    Constraints:
                    Keep the change bounded.
                    """, "gpt-5.6-luna", null, states::add, activities::add);

            assertEquals(EngineeringTask.Status.PROPOSED, proposal.status());
            assertEquals("Establish the requested change.", proposal.goal());
            assertEquals("Keep the change bounded.", proposal.constraints());
            assertTrue(proposal.allowedActions().contains("After approval"));
            assertEquals("Unrelated changes.", proposal.outOfScope());
            assertEquals(List.of("Approval is explicit."), proposal.acceptanceCriteria());
            assertEquals("Run existing focused tests.", proposal.verificationApproach());
            assertEquals("AI-generated proposal.", proposal.proposalSource());
            assertTrue(states.contains(EngineOsService.ExecutionState.WAITING_FOR_APPROVAL));
            assertEquals(0, service.submissions.get());
            assertTrue(activities.stream().anyMatch(value -> value.contains("PROPOSED")));

            var result = client.approve(proposal.requestId(), states::add, activities::add);

            assertEquals(EngineOsService.ExecutionState.GREEN, result.state());
            assertEquals("ok", result.detail());
            assertEquals(new ObservationRunner.ObservationResult(0, "observed", ""), result.observation());
            assertEquals(1, service.submissions.get());
            assertFalse(client.showActiveVerificationTarget());
        } finally {
            server.close();
        }
    }

    @Test
    void rejectionIsRecordedAndNeverCallsCoder() {
        var service = new RecordingService();
        var server = new EngineOsHttpServer(service);
        server.start();
        try {
            EngineOsService client = new HttpEngineOsService(server.baseUri());
            var proposal = client.propose("desktop-id", project, "Inspect current behavior.",
                    "test-model", null, state -> { }, activity -> { });

            var result = client.reject(proposal.requestId());

            assertEquals(EngineOsService.ExecutionState.REJECTED, result.state());
            assertTrue(result.activity().stream().anyMatch(value -> value.contains("rejected")));
            assertEquals(1, service.rejections.get());
            assertEquals(0, service.submissions.get());
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
        var service = new HttpEngineOsService(URI.create("http://127.0.0.1:" + port));

        var failure = assertThrows(IllegalStateException.class, service::codingAgentProvider);

        assertTrue(failure.getMessage().contains("/api/engineos/provider"));
        assertTrue(failure.getMessage().contains("127.0.0.1:" + port));
        assertTrue(failure.getMessage().contains("ConnectException"));
    }

    @Test
    void forwardsExecutionActivityAfterApproval() throws Exception {
        var service = new DelayedRecordingService();
        var server = new EngineOsHttpServer(service);
        server.start();
        var executor = Executors.newSingleThreadExecutor();
        try {
            var client = new HttpEngineOsService(server.baseUri());
            var proposal = client.propose("desktop-id", project, "Implement the change.",
                    "test-model", null, state -> { }, activity -> { });
            var states = new CopyOnWriteArrayList<EngineOsService.ExecutionState>();
            var activities = new CopyOnWriteArrayList<String>();
            var future = executor.submit(() -> client.approve(proposal.requestId(),
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
    void cancellationDuringApprovedExecutionKeepsServiceUsable() throws Exception {
        var service = new ControlledCancellationService();
        var server = new EngineOsHttpServer(service);
        server.start();
        var executor = Executors.newSingleThreadExecutor();
        try {
            var client = new HttpEngineOsService(server.baseUri());
            var proposal = client.propose("desktop-id", project, "first", "test",
                    null, state -> { }, activity -> { });
            var first = executor.submit(() -> client.approve(proposal.requestId(),
                    state -> { }, activity -> { }));

            assertTrue(service.started.await(2, TimeUnit.SECONDS));
            boolean cancellationSent = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (!cancellationSent && System.nanoTime() < deadline) {
                cancellationSent = client.cancelRequest(proposal.requestId());
                if (!cancellationSent) {
                    Thread.sleep(10);
                }
            }
            assertTrue(cancellationSent);
            assertEquals(EngineOsService.ExecutionState.CANCELLED, first.get(2, TimeUnit.SECONDS).state());
            assertEquals(1, service.cancellations.get());

            var subsequentProposal = client.propose("desktop-id", project, "second", "test",
                    null, state -> { }, activity -> { });
            var subsequent = client.approve(subsequentProposal.requestId(),
                    state -> { }, activity -> { });
            assertEquals(EngineOsService.ExecutionState.GREEN, subsequent.state());
            assertEquals(2, service.submissions.get());
        } finally {
            service.release.countDown();
            executor.shutdownNow();
            server.close();
        }
    }

    @Test
    void exposesProposalDecisionAndResultEndpoints() throws Exception {
        var server = new EngineOsHttpServer(new RecordingService());
        server.start();
        try {
            var http = HttpClient.newHttpClient();
            var mapper = new ObjectMapper().findAndRegisterModules();
            var body = mapper.writeValueAsBytes(
                    new EngineOsHttpServer.SubmitRequest(project.toString(), "Inspect.", "test"));
            var acceptedResponse = http.send(
                    HttpRequest.newBuilder(server.baseUri().resolve("/api/engineos/requests"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(202, acceptedResponse.statusCode());
            String requestId = mapper.readValue(
                    acceptedResponse.body(), EngineOsHttpServer.RequestAccepted.class).requestId();

            var statusUri = server.baseUri().resolve("/api/engineos/requests/" + requestId);
            var proposalUri = server.baseUri().resolve(statusUri + "/proposal");
            var activityUri = server.baseUri().resolve(statusUri + "/activity");
            var resultUri = server.baseUri().resolve(statusUri + "/result");
            assertEquals(200, get(http, proposalUri));
            assertEquals("PROPOSED",
                    mapper.readTree(getBody(http, proposalUri)).get("status").asText());
            assertEquals(EngineOsService.ExecutionState.WAITING_FOR_APPROVAL,
                    mapper.readValue(getBody(http, statusUri), EngineOsHttpServer.RequestStatus.class).state());
            assertEquals(200, get(http, activityUri));
            assertEquals(409, get(http, resultUri));

            assertEquals(202, post(http, server.baseUri().resolve(
                    "/api/engineos/requests/" + requestId + "/approve")));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (!mapper.readValue(getBody(http, statusUri), EngineOsHttpServer.RequestStatus.class).complete()
                    && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertEquals(200, get(http, resultUri));
            assertEquals(404, get(http, server.baseUri().resolve(
                    "/api/engineos/requests/unknown")));
        } finally {
            server.close();
        }
    }

    private static int get(HttpClient http, URI uri) throws Exception {
        return http.send(HttpRequest.newBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()).statusCode();
    }

    private static String getBody(HttpClient http, URI uri) throws Exception {
        return new String(http.send(HttpRequest.newBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()).body());
    }

    private static int post(HttpClient http, URI uri) throws Exception {
        return http.send(HttpRequest.newBuilder(uri).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofByteArray()).statusCode();
    }

    private class RecordingService implements EngineOsService {
        protected final AtomicInteger submissions = new AtomicInteger();
        protected final AtomicInteger rejections = new AtomicInteger();

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
        public TaskProposal propose(
                String requestId, Path project, String prompt, String selectedModel,
                com.engineos.infrastructure.git.GitSnapshot baseline,
                java.util.function.Consumer<ExecutionState> stateSink,
                java.util.function.Consumer<String> activitySink) {
            stateSink.accept(ExecutionState.STARTING);
            activitySink.accept("proposal activity");
            stateSink.accept(ExecutionState.WAITING_FOR_APPROVAL);
            return new TaskProposal(requestId, "task-id", "Inspect workspace",
                    "Establish the requested change.", prompt, "Keep the change bounded.",
                    "Start with evidence.", "After approval, coder may act.", EngineeringTask.Status.PROPOSED,
                    "Unrelated changes.", List.of("Approval is explicit."),
                    "Run existing focused tests.", "AI-generated proposal.");
        }

        @Override
        public ExecutionResult approve(
                String requestId,
                java.util.function.Consumer<ExecutionState> stateSink,
                java.util.function.Consumer<String> activitySink) {
            submissions.incrementAndGet();
            stateSink.accept(ExecutionState.CODING_AGENT_WORKING);
            activitySink.accept("activity");
            stateSink.accept(ExecutionState.GREEN);
            return new ExecutionResult(ExecutionState.GREEN, "ok", null,
                    new ObservationRunner.ObservationResult(0, "observed", ""),
                    List.of("activity"), null);
        }

        @Override
        public ExecutionResult reject(String requestId) {
            rejections.incrementAndGet();
            return new ExecutionResult(ExecutionState.REJECTED, "rejected", null, null,
                    List.of("Human rejected proposal."), null);
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
        public ExecutionResult approve(
                String requestId,
                java.util.function.Consumer<ExecutionState> stateSink,
                java.util.function.Consumer<String> activitySink) {
            stateSink.accept(ExecutionState.CODING_AGENT_WORKING);
            activitySink.accept("working");
            started.countDown();
            awaitRelease();
            stateSink.accept(ExecutionState.GREEN);
            return new ExecutionResult(ExecutionState.GREEN, "ok", null,
                    new ObservationRunner.ObservationResult(0, "observed", ""),
                    List.of("working"), null);
        }

        private void awaitRelease() {
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", exception);
            }
        }
    }

    private final class ControlledCancellationService extends RecordingService {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public ExecutionResult approve(
                String requestId,
                java.util.function.Consumer<ExecutionState> stateSink,
                java.util.function.Consumer<String> activitySink) {
            int request = submissions.incrementAndGet();
            if (request > 1) {
                return new ExecutionResult(ExecutionState.GREEN, "ok", null,
                        new ObservationRunner.ObservationResult(0, "observed", ""), List.of(), null);
            }
            stateSink.accept(ExecutionState.CODING_AGENT_WORKING);
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return new ExecutionResult(ExecutionState.CANCELLED,
                    "CANCELLED - Request cancelled.", null, null, List.of("started"), null);
        }

        @Override
        public boolean cancelRequest(String requestId) {
            cancellations.incrementAndGet();
            release.countDown();
            return true;
        }

        private final AtomicInteger cancellations = new AtomicInteger();
    }
}
