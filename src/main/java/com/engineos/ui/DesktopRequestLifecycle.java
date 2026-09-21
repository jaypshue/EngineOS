package com.engineos.ui;

import com.engineos.application.EngineOsService;

/**
 * UI-owned request lifecycle state. Observation actions must not mutate it.
 */
public final class DesktopRequestLifecycle {
    public enum State {
        RECOGNIZED,
        SUBMITTED,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    private State state = State.RECOGNIZED;
    private boolean resultAvailable;
    private boolean codingAgentActive;

    public State state() {
        return state;
    }

    public boolean resultAvailable() {
        return resultAvailable;
    }

    public boolean active() {
        return state == State.SUBMITTED || state == State.RUNNING;
    }

    public boolean codingAgentActive() {
        return codingAgentActive;
    }

    public boolean stopAvailable() {
        return active();
    }

    public void submitted() {
        state = State.SUBMITTED;
        resultAvailable = false;
        codingAgentActive = true;
    }

    public void serviceState(EngineOsService.ExecutionState serviceState) {
        if (serviceState == null) {
            return;
        }
        switch (serviceState) {
            case STARTING -> state = State.SUBMITTED;
            case CODING_AGENT_WORKING -> {
                state = State.RUNNING;
                codingAgentActive = true;
            }
            case RUNNING_OBSERVATION -> {
                state = State.RUNNING;
                codingAgentActive = false;
            }
            case GREEN -> {
                state = State.COMPLETED;
                resultAvailable = true;
                codingAgentActive = false;
            }
            case FAILED -> {
                state = State.FAILED;
                resultAvailable = true;
                codingAgentActive = false;
            }
            case CANCELLED -> {
                state = State.CANCELLED;
                resultAvailable = true;
                codingAgentActive = false;
            }
        }
    }

    public void cancelled() {
        state = State.CANCELLED;
        resultAvailable = true;
        codingAgentActive = false;
    }

    public void replaceWithRecognizedRequest() {
        if (active()) {
            return;
        }
        state = State.RECOGNIZED;
        resultAvailable = false;
    }

    public String displayText() {
        return switch (state) {
            case RECOGNIZED -> "Request recognized; not submitted.";
            case SUBMITTED -> "Request starting...";
            case RUNNING -> "Request running...";
            case COMPLETED -> resultAvailable
                    ? "Request completed; Engineering Result available."
                    : "Request completed.";
            case FAILED -> resultAvailable
                    ? "Request failed/terminated; Engineering Result available."
                    : "Request failed/terminated.";
            case CANCELLED -> "Request cancelled/terminated; Engineering Result available.";
        };
    }
}
