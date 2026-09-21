package com.engineos.observation;

import com.engineos.domain.EngineeringRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectedVerificationPlanTest {
    @Test
    void derivesExecutableCategoriesAndStepsFromRequestSections() {
        var request = EngineeringRequest.parse("""
                ENGINEOS_REQUEST_V1

                ENGINEERING REQUEST

                Goal:
                Verify the Desktop to Service HTTP API.

                Acceptance Criteria:
                - readiness returns 200
                - activity appears before the final result

                Verification:
                Start the standalone Service and Desktop, then exercise the request.
                """);

        var plan = DirectedVerificationPlan.from(request);

        assertTrue(plan.categories().contains(DirectedVerificationPlan.Category.API));
        assertTrue(plan.categories().contains(DirectedVerificationPlan.Category.SERVICE));
        assertTrue(plan.categories().contains(DirectedVerificationPlan.Category.UI));
        assertTrue(plan.steps().contains("CALL REQUESTED API ENDPOINTS"));
        assertEquals(2, plan.acceptanceCriteria().size());
    }
}
