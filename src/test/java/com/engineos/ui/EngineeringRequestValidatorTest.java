package com.engineos.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineeringRequestValidatorTest {
    @Test
    void recognizesExactProtocolMarker() {
        assertTrue(EngineeringRequestValidator.isValid(
                "Goal: verify the workflow.\nENGINEOS_REQUEST_V1\nAcceptance: tests pass."));
    }

    @Test
    void acceptsAPlainEngineeringRequest() {
        assertTrue(EngineeringRequestValidator.isValid("Please investigate the failing system."));
    }

    @Test
    void updatesAsRequestTextChanges() {
        String request = "Goal: investigate.";
        assertTrue(EngineeringRequestValidator.isValid(request));
        request += "\nENGINEOS_REQUEST_V1";
        assertTrue(EngineeringRequestValidator.isValid(request));
        request = request.replace(EngineeringRequestValidator.MARKER, "");
        assertTrue(EngineeringRequestValidator.isValid(request));
        assertFalse(EngineeringRequestValidator.isValid("   "));
    }
}
