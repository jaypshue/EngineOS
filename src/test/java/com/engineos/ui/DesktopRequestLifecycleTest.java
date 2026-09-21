package com.engineos.ui;

import com.engineos.application.EngineOsService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesktopRequestLifecycleTest {
    @Test
    void tracksRecognizedSubmittedRunningAndCompletedRequest() {
        var lifecycle = new DesktopRequestLifecycle();

        assertEquals(DesktopRequestLifecycle.State.RECOGNIZED, lifecycle.state());
        assertFalse(lifecycle.stopAvailable());
        lifecycle.submitted();
        assertEquals(DesktopRequestLifecycle.State.SUBMITTED, lifecycle.state());
        assertTrue(lifecycle.stopAvailable());
        assertTrue(lifecycle.codingAgentActive());
        assertEquals("Request starting...", lifecycle.displayText());
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
        assertTrue(lifecycle.resultAvailable());
        assertEquals("Request failed/terminated; Engineering Result available.",
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
        assertTrue(lifecycle.codingAgentActive());

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
    void cancelledRequestExposesItsCompletedResult() {
        var lifecycle = new DesktopRequestLifecycle();
        lifecycle.submitted();
        lifecycle.serviceState(EngineOsService.ExecutionState.CODING_AGENT_WORKING);
        lifecycle.serviceState(EngineOsService.ExecutionState.CANCELLED);

        assertEquals(DesktopRequestLifecycle.State.CANCELLED, lifecycle.state());
        assertTrue(lifecycle.resultAvailable());
        assertEquals("Request cancelled/terminated; Engineering Result available.",
                lifecycle.displayText());
        assertFalse(lifecycle.stopAvailable());
    }
}
