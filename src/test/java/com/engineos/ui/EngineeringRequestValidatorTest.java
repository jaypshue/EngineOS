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
    void rejectsRequestWithoutProtocolMarker() {
        assertFalse(EngineeringRequestValidator.isValid("Please fix the failing test."));
    }

    @Test
    void updatesAsRequestTextChanges() {
        String request = "Goal: investigate.";
        assertFalse(EngineeringRequestValidator.isValid(request));
        request += "\nENGINEOS_REQUEST_V1";
        assertTrue(EngineeringRequestValidator.isValid(request));
        request = request.replace(EngineeringRequestValidator.MARKER, "");
        assertFalse(EngineeringRequestValidator.isValid(request));
    }
}
