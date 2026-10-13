package com.engineos.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionPolicyTest {
    @Test
    void implementationAllowsChangesAndVerificationDoesNot() {
        var implementation = ExecutionPolicy.defaults(ExecutionPhase.IMPLEMENTATION);
        var verification = ExecutionPolicy.defaults(ExecutionPhase.VERIFICATION);

        assertTrue(implementation.workspaceModificationAllowed());
        assertFalse(verification.workspaceModificationAllowed());
        assertTrue(verification.maximumDuration().compareTo(Duration.ZERO) > 0);
        assertEquals(1, verification.maximumCorrectionAttempts());
    }

    @Test
    void verificationRequestSelectsVerificationPhase() {
        var request = EngineeringRequest.parse("""
                ENGINEOS_REQUEST_V1
                Constraints:
                Do not change source files.
                """);

        var policy = ExecutionPolicy.forRequest(request);

        assertEquals(ExecutionPhase.VERIFICATION, policy.phase());
        assertFalse(policy.workspaceModificationAllowed());
    }

    @Test
    void hyphenatedVerificationOnlyRequestUsesVerificationPolicy() {
        var request = EngineeringRequest.parse("""
                ENGINEOS_REQUEST_V1
                Goal:
                Run a verification-only request.
                """);

        var policy = ExecutionPolicy.forRequest(request);

        assertEquals(ExecutionPhase.VERIFICATION, policy.phase());
        assertEquals(Duration.ofSeconds(120), policy.maximumDuration());
        assertFalse(policy.workspaceModificationAllowed());
    }

    @Test
    void implementationRequestRemainsImplementationPolicy() {
        var request = EngineeringRequest.parse("""
                ENGINEOS_REQUEST_V1
                Goal:
                Implement the requested change.
                """);

        var policy = ExecutionPolicy.forRequest(request);

        assertEquals(ExecutionPhase.IMPLEMENTATION, policy.phase());
        assertEquals(Duration.ofSeconds(600), policy.maximumDuration());
        assertTrue(policy.workspaceModificationAllowed());
    }

    @Test
    void existingSystemInvestigationUsesTheCodingAgentWithoutAllowingChanges() {
        var request = EngineeringRequest.parse("""
                Investigate this existing system and explain its architecture.
                Do not modify the repository.
                """);

        var policy = ExecutionPolicy.forRequest(request);

        assertEquals(ExecutionPhase.INVESTIGATION, policy.phase());
        assertFalse(policy.workspaceModificationAllowed());
    }
}
