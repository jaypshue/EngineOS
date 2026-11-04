package com.engineos.ui;

import com.engineos.application.EngineOsService;

/**
 * UI-owned request lifecycle state. Observation actions must not mutate it.
 */
public final class DesktopRequestLifecycle {
    public enum State {
        RECOGNIZED,
        SUBMITTED,
        AWAITING_APPROVAL,
        APPROVED,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED,
        REJECTED
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
        return state == State.SUBMITTED || state == State.AWAITING_APPROVAL
                || state == State.APPROVED || state == State.RUNNING;
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
        codingAgentActive = false;
    }

    public void serviceState(EngineOsService.ExecutionState serviceState) {
        if (serviceState == null) {
            return;
        }
        switch (serviceState) {
            case STARTING -> state = State.SUBMITTED;
            case WAITING_FOR_APPROVAL -> {
                state = State.AWAITING_APPROVAL;
                codingAgentActive = false;
            }
            case APPROVED -> {
                state = State.APPROVED;
                codingAgentActive = false;
            }
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
                resultAvailable = false;
                codingAgentActive = false;
            }
            case CANCELLED -> {
                state = State.CANCELLED;
                resultAvailable = false;
                codingAgentActive = false;
            }
            case REJECTED -> {
                state = State.REJECTED;
                resultAvailable = true;
                codingAgentActive = false;
            }
        }
    }

    public void cancelled() {
        state = State.CANCELLED;
        resultAvailable = false;
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
            case AWAITING_APPROVAL -> "Proposed task awaiting your approval.";
            case APPROVED -> "Task approved; execution starting...";
            case RUNNING -> "Request running...";
            case COMPLETED -> resultAvailable
                    ? "Request completed; Engineering Result available."
                    : "Request completed.";
            case FAILED -> resultAvailable
                    ? "Request failed/terminated; Engineering Result available."
                    : "Request failed/terminated.";
            case CANCELLED -> "Request cancelled/terminated.";
            case REJECTED -> resultAvailable
                    ? "Proposed task rejected; Engineering Result available."
                    : "Proposed task rejected.";
        };
    }
}
