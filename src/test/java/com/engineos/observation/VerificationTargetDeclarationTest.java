package com.engineos.observation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificationTargetDeclarationTest {
    @Test
    void parsesExplicitEngineOsTarget() {
        var declaration = VerificationTargetDeclaration.parse(
                "ENGINEOS_REQUEST_V1\nVerification Target: EngineOS");

        assertEquals(VerificationTarget.Kind.ENGINEOS, declaration.requestedTarget().orElseThrow());
        assertTrue(declaration.explicit());
        assertTrue(declaration.error().isBlank());
    }

    @Test
    void parsesExplicitIntAssistTarget() {
        var declaration = VerificationTargetDeclaration.parse(
                "ENGINEOS_REQUEST_V1\nVerification Target: IntAssist");

        assertEquals(VerificationTarget.Kind.INTASSIST, declaration.requestedTarget().orElseThrow());
        assertTrue(declaration.explicit());
    }

    @Test
    void parsesExplicitMavenTarget() {
        var declaration = VerificationTargetDeclaration.parse(
                "ENGINEOS_REQUEST_V1\nVerification Target: Maven");

        assertEquals(VerificationTarget.Kind.MAVEN, declaration.requestedTarget().orElseThrow());
        assertTrue(declaration.explicit());
        assertTrue(declaration.error().isBlank());
    }

    @Test
    void missingTargetIsCompatibleAndUnspecified() {
        var declaration = VerificationTargetDeclaration.parse("ENGINEOS_REQUEST_V1\nGoal: run tests");

        assertTrue(declaration.requestedTarget().isEmpty());
        assertFalse(declaration.explicit());
        assertTrue(declaration.error().isBlank());
    }

    @Test
    void unsupportedTargetIsReportedWithoutGuessing() {
        var declaration = VerificationTargetDeclaration.parse(
                "ENGINEOS_REQUEST_V1\nVerification Target: OtherApp");

        assertTrue(declaration.explicit());
        assertTrue(declaration.requestedTarget().isEmpty());
        assertTrue(declaration.error().contains("OtherApp"));
    }
}
