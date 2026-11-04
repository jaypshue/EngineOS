package com.engineos.ui;

import com.engineos.application.EngineOsService;
import com.engineos.domain.EngineeringTask;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesktopRequestLifecycleTest {
    @Test
    void proposalPresentationIncludesGoalScopeConstraintsAndCoderPermissions() {
        var proposal = new EngineOsService.TaskProposal(
                "request-1", "task-1", "Inspect workspace", "Understand current behavior.",
                "Review the service boundary.", "Do not modify source files.",
                "Start from repository evidence.", "No workspace changes are permitted.",
                EngineeringTask.Status.PROPOSED,
                "Unrelated refactors.", java.util.List.of("Explain the decision boundary."),
                "Use the existing approval tests.", "AI-generated proposal.");

        String text = ObservationWindow.proposalText(proposal);

        assertTrue(text.contains("Status: PROPOSED"));
        assertTrue(text.contains("Goal:\nUnderstand current behavior."));
        assertTrue(text.contains("In scope:\nReview the service boundary."));
        assertTrue(text.contains("Constraints:\nDo not modify source files."));
        assertTrue(text.contains("What the Coder may do:\nNo workspace changes are permitted."));
        assertTrue(text.contains("Out of scope:\nUnrelated refactors."));
        assertTrue(text.contains("Acceptance criteria:\n- Explain the decision boundary."));
        assertTrue(text.contains("Suggested verification:\nUse the existing approval tests."));
        assertTrue(text.contains("Proposal source:\nAI-generated proposal."));
    }

    @Test
    void tracksRecognizedSubmittedRunningAndCompletedRequest() {
        var lifecycle = new DesktopRequestLifecycle();

        assertEquals(DesktopRequestLifecycle.State.RECOGNIZED, lifecycle.state());
        assertFalse(lifecycle.stopAvailable());
        lifecycle.submitted();
        assertEquals(DesktopRequestLifecycle.State.SUBMITTED, lifecycle.state());
        assertTrue(lifecycle.stopAvailable());
        assertFalse(lifecycle.codingAgentActive());
        assertEquals("Request starting...", lifecycle.displayText());
        lifecycle.serviceState(EngineOsService.ExecutionState.WAITING_FOR_APPROVAL);
        assertEquals(DesktopRequestLifecycle.State.AWAITING_APPROVAL, lifecycle.state());
        assertFalse(lifecycle.codingAgentActive());
        assertEquals("Proposed task awaiting your approval.", lifecycle.displayText());
        lifecycle.serviceState(EngineOsService.ExecutionState.APPROVED);
        assertEquals(DesktopRequestLifecycle.State.APPROVED, lifecycle.state());
        lifecycle.serviceState(EngineOsService.ExecutionState.CODING_AGENT_WORKING);
        assertEquals(DesktopRequestLifecycle.State.RUNNING, lifecycle.state());
        assertTrue(lifecycle.stopAvailable());
        lifecycle.serviceState(EngineOsService.ExecutionState.GREEN);
        assertEquals(DesktopRequestLifecycle.State.COMPLETED, lifecycle.state());
        assertTrue(lifecycle.resultAvailable());
        assertFalse(lifecycle.stopAvailable());
    }

    @Test
    void failedRequestRemainsFailedUntilReplaced() {
        var lifecycle = new DesktopRequestLifecycle();
        lifecycle.submitted();
        lifecycle.serviceState(EngineOsService.ExecutionState.FAILED);

        assertEquals(DesktopRequestLifecycle.State.FAILED, lifecycle.state());
        assertFalse(lifecycle.resultAvailable());
        assertEquals("Request failed/terminated.",
                lifecycle.displayText());

        lifecycle.replaceWithRecognizedRequest();
        assertEquals(DesktopRequestLifecycle.State.RECOGNIZED, lifecycle.state());
    }

    @Test
    void observationActionsDoNotChangeRequestLifecycle() {
        var lifecycle = new DesktopRequestLifecycle();
        lifecycle.submitted();
        lifecycle.serviceState(EngineOsService.ExecutionState.CODING_AGENT_WORKING);

        // Observation feedback is intentionally not part of this state object.
        assertEquals(DesktopRequestLifecycle.State.RUNNING, lifecycle.state());
        assertEquals("Request running...", lifecycle.displayText());
    }

    @Test
    void codingAgentActivityIsActiveOnlyWhileCodingAgentRuns() {
        var lifecycle = new DesktopRequestLifecycle();
        lifecycle.submitted();
        assertFalse(lifecycle.codingAgentActive());

        lifecycle.serviceState(EngineOsService.ExecutionState.WAITING_FOR_APPROVAL);
        assertFalse(lifecycle.codingAgentActive());
        lifecycle.serviceState(EngineOsService.ExecutionState.CODING_AGENT_WORKING);
        assertTrue(lifecycle.codingAgentActive());

        lifecycle.serviceState(EngineOsService.ExecutionState.GREEN);
        assertFalse(lifecycle.codingAgentActive());
    }

    @Test
    void codingAgentActivityStopsBeforeObservationBegins() {
        var lifecycle = new DesktopRequestLifecycle();
        lifecycle.submitted();
        lifecycle.serviceState(EngineOsService.ExecutionState.CODING_AGENT_WORKING);
        lifecycle.serviceState(EngineOsService.ExecutionState.RUNNING_OBSERVATION);

        assertFalse(lifecycle.codingAgentActive());
    }

    @Test
    void editingRequestCannotMakeSendAvailableDuringActiveExecution() {
        var lifecycle = new DesktopRequestLifecycle();
        lifecycle.submitted();
        lifecycle.serviceState(EngineOsService.ExecutionState.CODING_AGENT_WORKING);

        lifecycle.replaceWithRecognizedRequest();

        assertEquals(DesktopRequestLifecycle.State.RUNNING, lifecycle.state());
        assertTrue(lifecycle.stopAvailable());
    }

    @Test
    void cancelledRequestDoesNotExposeAnEngineeringResult() {
        var lifecycle = new DesktopRequestLifecycle();
        lifecycle.submitted();
        lifecycle.serviceState(EngineOsService.ExecutionState.CODING_AGENT_WORKING);
        lifecycle.serviceState(EngineOsService.ExecutionState.CANCELLED);

        assertEquals(DesktopRequestLifecycle.State.CANCELLED, lifecycle.state());
        assertFalse(lifecycle.resultAvailable());
        assertEquals("Request cancelled/terminated.",
                lifecycle.displayText());
        assertFalse(lifecycle.stopAvailable());
    }

    @Test
    void rejectedProposalIsTerminalAndHasARecordedResult() {
        var lifecycle = new DesktopRequestLifecycle();
        lifecycle.submitted();
        lifecycle.serviceState(EngineOsService.ExecutionState.WAITING_FOR_APPROVAL);
        lifecycle.serviceState(EngineOsService.ExecutionState.REJECTED);

        assertEquals(DesktopRequestLifecycle.State.REJECTED, lifecycle.state());
        assertTrue(lifecycle.resultAvailable());
        assertFalse(lifecycle.codingAgentActive());
        assertFalse(lifecycle.stopAvailable());
        assertEquals("Proposed task rejected; Engineering Result available.", lifecycle.displayText());
    }
}
