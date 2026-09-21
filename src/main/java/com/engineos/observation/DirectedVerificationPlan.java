package com.engineos.observation;

import com.engineos.domain.EngineeringRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Small request-driven verification plan. Steps are executable intents for the
 * existing runners, not a replacement automation framework.
 */
public record DirectedVerificationPlan(
        List<Category> categories,
        List<String> steps,
        List<String> acceptanceCriteria) {

    public DirectedVerificationPlan {
        categories = categories == null ? List.of() : List.copyOf(categories);
        steps = steps == null ? List.of() : List.copyOf(steps);
        acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
    }

    public static DirectedVerificationPlan from(EngineeringRequest request) {
        String source = request.raw().toLowerCase();
        List<Category> categories = new ArrayList<>();
        if (containsAny(source, "compile", "build", "test", "source", "code")) {
            categories.add(Category.CODE);
        }
        if (containsAny(source, "http", "api/", "endpoint", "requestid", "readiness")) {
            categories.add(Category.API);
        }
        if (containsAny(source, "service", "process", "pid", "standalone", "restart")) {
            categories.add(Category.SERVICE);
        }
        if (containsAny(source, "desktop", "ui", "window", "click", "screenshot", "visible")) {
            categories.add(Category.UI);
        }
        if (containsAny(source, "end-to-end", "end to end", "e2e")
                || (categories.contains(Category.API) && categories.contains(Category.UI))) {
            categories.add(Category.END_TO_END);
        }
        if (categories.isEmpty()) {
            categories.add(Category.CODE);
        }

        List<String> steps = new ArrayList<>();
        if (categories.contains(Category.CODE)) {
            steps.add("INSPECT CHANGED SOURCE");
            steps.add("RUN TARGETED BUILD/TEST VERIFICATION");
        }
        if (categories.contains(Category.API)) {
            steps.add("CALL REQUESTED API ENDPOINTS");
            steps.add("VERIFY ACTUAL STATUS AND RESPONSE DATA");
        }
        if (categories.contains(Category.SERVICE)) {
            steps.add("VERIFY SERVICE PROCESS AND READINESS");
            steps.add("VERIFY SERVICE LIFECYCLE REMAINS HEALTHY");
        }
        if (categories.contains(Category.UI)) {
            steps.add("START APPLICATION");
            steps.add("LOCATE TARGET WINDOW");
            steps.add("VERIFY REQUESTED VISIBLE STATE");
            steps.add("CAPTURE SCREENSHOT WHEN VISUAL EVIDENCE IS REQUIRED");
        }
        if (categories.contains(Category.END_TO_END)) {
            steps.add("EXERCISE COMPLETE USER WORKFLOW");
            steps.add("MAP INTERMEDIATE AND FINAL EVIDENCE TO ACCEPTANCE CRITERIA");
        }
        return new DirectedVerificationPlan(categories, steps, request.acceptanceCriteria());
    }

    private static boolean containsAny(String source, String... values) {
        for (String value : values) {
            if (source.contains(value)) {
                return true;
            }
        }
        return false;
    }

    public enum Category {
        CODE, API, SERVICE, UI, END_TO_END
    }
}
