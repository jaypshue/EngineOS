package com.engineos.agent;

import com.engineos.domain.CodingTask;
import com.engineos.domain.EngineeringModel;
import com.engineos.domain.EngineeringTask;
import com.engineos.domain.WorkspaceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LlmEngineerAgentTest {
    @TempDir
    Path root;

    private static final String RESPONSE = """
            {
              "title": "Bound the request approval API",
              "goal": "Make request approval explicit.",
              "scope": "Review and update src/Approval.java and its focused tests.",
              "outOfScope": "Do not replace the HTTP stack or add autonomous planning.",
              "acceptanceCriteria": ["A pending request cannot execute before approval."],
              "constraints": "Preserve the current HTTP style.",
              "rationale": "The approval boundary is the smallest relevant change.",
              "verificationApproach": "Inspect existing test configuration and run the approval tests."
            }
            """;

    @Test
    void oneModelCallProducesRichProposedTaskUsingBoundedWorkspaceContext() {
        var calls = new AtomicInteger();
        var workspace = workspace();
        var model = new EngineeringModel("""
                ENGINEOS_REQUEST_V1
                Goal:
                Make request approval explicit.
                Task:
                Update the existing service boundary.
                Acceptance criteria:
                - No Coder call before approval.
                Constraints:
                Preserve the HTTP API.
                Verification:
                Run the existing focused tests.
                """);
        var engineer = agent(prompt -> {
            calls.incrementAndGet();
            assertTrue(prompt.contains("Java/Maven"));
            assertTrue(prompt.contains("Git branch: feature/approval"));
            assertTrue(prompt.contains("Git worktree clean: false"));
            assertTrue(prompt.contains("Approval.java"));
            assertTrue(prompt.contains(model.humanIntent()));
            assertFalse(prompt.contains("objects"));
            assertFalse(prompt.contains("secret.env"));
            assertFalse(prompt.contains("Generated.class"));
            return RESPONSE;
        });

        var task = engineer.proposeTask(workspace, model);

        assertEquals(1, calls.get());
        assertEquals(EngineeringTask.Status.PROPOSED, task.status());
        assertEquals("Bound the request approval API", task.title());
        assertEquals(model.humanIntent(), task.intent());
        assertEquals("Make request approval explicit.", task.proposal().goal());
        assertTrue(task.proposal().scope().contains("src/Approval.java"));
        assertTrue(task.proposal().outOfScope().contains("autonomous planning"));
        assertTrue(task.proposal().constraints().contains("Preserve the HTTP API."));
        assertTrue(task.proposal().acceptanceCriteria().stream()
                .anyMatch(value -> value.contains("No Coder call before approval.")));
        assertTrue(task.proposal().verificationApproach().contains("Run the existing focused tests."));
        assertTrue(task.proposalSource().contains("AI-generated"));
        assertEquals(1, model.tasks().size());
        assertEquals(1, model.investigations().size());
        assertTrue(model.conversation().getLast().contains("Acceptance criteria"));
        assertThrows(IllegalStateException.class, () -> new CodingTask(task, List.of()));

        task.approve();
        String execution = new CodingTask(task, List.of()).executionPrompt();
        assertTrue(execution.startsWith(model.humanIntent()));
        assertTrue(execution.contains("HUMAN-APPROVED ENGINEERING TASK"));
        assertTrue(execution.contains(task.proposal().outOfScope()));
        assertTrue(execution.contains(task.proposal().verificationApproach()));
    }

    @Test
    void modelFailureFallsBackToTailoredTaskWithoutApprovalOrLosingHumanRestrictions() {
        var calls = new AtomicInteger();
        var model = new EngineeringModel("""
                ENGINEOS_REQUEST_V1
                Goal:
                Investigate approval handling.
                Task:
                Inspect the request decision boundary.
                Constraints:
                Do not modify the repository.
                Acceptance criteria:
                Explain where approval is checked.
                """);
        var task = agent(prompt -> {
            calls.incrementAndGet();
            throw new LlmEngineerAgent.ProposalGenerationException("Engineer CLI exited with code 1");
        }).proposeTask(workspace(), model);

        assertEquals(1, calls.get());
        assertEquals(EngineeringTask.Status.PROPOSED, task.status());
        assertEquals("Investigate approval handling.", task.proposal().goal());
        assertTrue(task.proposal().scope().contains("Inspect the request decision boundary."));
        assertTrue(task.proposal().scope().contains("Approval.java"));
        assertTrue(task.rationale().contains("Java/Maven"));
        assertTrue(task.proposal().constraints().contains("workspace modifications are prohibited"));
        assertTrue(task.proposal().acceptanceCriteria().contains("Explain where approval is checked."));
        assertTrue(task.proposal().verificationApproach().contains("existing tests"));
        assertTrue(task.proposalSource().contains("Deterministic fallback"));
        assertTrue(task.proposalSource().contains("exited with code 1"));
        assertEquals(1, model.tasks().size());
        assertTrue(model.conversation().getLast().contains("Deterministic fallback"));
    }

    @Test
    void malformedIncompleteOrOversizedResponsesUseFallbackRatherThanPartialPlans() {
        for (String invalid : List.of("not json", "{}", "null", RESPONSE.replace("\"scope\":", "\"unexpected\":"),
                RESPONSE.replace("Make request approval explicit.", ""),
                RESPONSE.replace("[\"A pending request cannot execute before approval.\"]", "[]"),
                RESPONSE + "\n{}", "x".repeat(40001))) {
            var model = new EngineeringModel("Fix approval handling.");
            var task = agent(prompt -> invalid).proposeTask(workspace(), model);
            assertEquals(EngineeringTask.Status.PROPOSED, task.status());
            assertTrue(task.proposalSource().contains("Deterministic fallback"));
            assertEquals(1, model.tasks().size());
        }
    }

    @Test
    void emptyOptionalRequestSectionsStillProduceAUsableFallback() {
        var task = agent(prompt -> "")
                .proposeTask(workspace(), new EngineeringModel("""
                        Inspect the approval boundary.
                        Goal:
                        Task:
                        Verification:
                        """));
        assertEquals(EngineeringTask.Status.PROPOSED, task.status());
        assertFalse(task.proposal().goal().isBlank());
        assertFalse(task.proposal().scope().isBlank());
        assertFalse(task.proposal().verificationApproach().isBlank());
        assertTrue(task.proposalSource().contains("Deterministic fallback"));
    }

    @Test
    void verificationAndInvestigationBoundariesRemainAuthoritativeForAiProposals() {
        for (String request : List.of("Verification only. Do not change source files.",
                "Investigate the existing system. Do not modify the repository.")) {
            var task = agent(prompt -> RESPONSE).proposeTask(workspace(), new EngineeringModel(request));
            assertEquals(EngineeringTask.Status.PROPOSED, task.status());
            assertTrue(task.proposal().constraints().contains("workspace modifications are prohibited"));
            assertTrue(task.proposal().constraints().contains("No execution before explicit human approval."));
        }
    }

    @Test
    void largeRequestFallsBackWithoutCallingModelOrTruncatingIntent() {
        String request = "Inspect this repository. " + "x".repeat(12000);
        var task = agent(prompt -> fail("Oversized requests must not be sent"))
                .proposeTask(workspace(), new EngineeringModel(request));
        assertEquals(request, task.intent());
        assertTrue(task.proposalSource().contains("Deterministic fallback"));
    }

    @Test
    void interruptedProposalDoesNotBecomeAnApprovableFallback() {
        try {
            var engineer = agent(prompt -> {
                Thread.currentThread().interrupt();
                throw new LlmEngineerAgent.ProposalGenerationException("Interrupted");
            });
            assertThrows(IllegalStateException.class, () -> engineer.proposeTask(
                    workspace(), new EngineeringModel("Inspect approval.")));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void acceptsJsonCodeFenceAndLabelsUnverifiedAssumptions() {
        var task = agent(prompt -> "```json\n" + RESPONSE + "\n```")
                .proposeTask(workspace(), new EngineeringModel("Fix approval handling."));
        assertTrue(task.proposalSource().contains("AI-generated"));
        assertTrue(task.proposalSource().contains("not verified"));
    }

    private WorkspaceContext workspace() {
        return new WorkspaceContext(root, "EngineOS", "feature/approval", false,
                List.of(root.resolve("pom.xml"), root.resolve("src/Approval.java"),
                        root.resolve(".git/objects/id"), root.resolve("target/Generated.class"),
                        root.resolve(".private/secret.env")));
    }

    private LlmEngineerAgent agent(LlmEngineerAgent.Completion completion) {
        return new LlmEngineerAgent(new AgentProvider("test", "reasoning-model"), completion);
    }
}
