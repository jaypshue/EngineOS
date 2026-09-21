package com.engineos.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsed view of ENGINEOS_REQUEST_V1. The original request text remains
 * unchanged and is still passed to the Coding Agent.
 */
public record EngineeringRequest(
        String raw,
        Map<String, String> sections,
        List<String> acceptanceCriteria) {

    public EngineeringRequest {
        raw = raw == null ? "" : raw;
        sections = sections == null ? Map.of() : Map.copyOf(sections);
        acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
    }

    public static EngineeringRequest parse(String raw) {
        String source = raw == null ? "" : raw;
        Map<String, String> sections = new LinkedHashMap<>();
        String current = null;
        StringBuilder value = new StringBuilder();
        for (String line : source.split("\\R", -1)) {
            String heading = heading(line);
            if (heading != null) {
                if (current != null) {
                    sections.put(current, value.toString().trim());
                }
                current = heading;
                value.setLength(0);
            } else if (current != null) {
                if (value.length() > 0) {
                    value.append(System.lineSeparator());
                }
                value.append(line);
            }
        }
        if (current != null) {
            sections.put(current, value.toString().trim());
        }
        return new EngineeringRequest(source, sections, criteria(sections.get("ACCEPTANCE CRITERIA")));
    }

    private static String heading(String line) {
        String trimmed = line.trim();
        if (trimmed.endsWith(":")) {
            String candidate = trimmed.substring(0, trimmed.length() - 1).trim().toUpperCase();
            return switch (candidate) {
                case "GOAL", "CONTEXT", "TASK", "ACCEPTANCE CRITERIA", "CONSTRAINTS",
                        "VERIFICATION", "EVIDENCE REQUIRED" -> candidate;
                default -> null;
            };
        }
        return null;
    }

    private static List<String> criteria(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String criterion = line.trim().replaceFirst("^(?:[-*]|\\d+[.)])\\s*", "");
            if (!criterion.isBlank() && !criterion.equals(line.trim())) {
                result.add(criterion);
            }
        }
        return result;
    }
}
