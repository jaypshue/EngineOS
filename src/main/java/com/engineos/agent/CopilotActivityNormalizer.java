package com.engineos.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class CopilotActivityNormalizer {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_ACTIVITY_LENGTH = 180;

    private CopilotActivityNormalizer() {
    }

    static Optional<String> normalize(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }
        String text = line.trim();
        try {
            JsonNode event = JSON.readTree(text);
            if (event == null || !event.isObject()) {
                return usefulText(text);
            }
            return eventActivity(event);
        } catch (Exception ignored) {
            return text.startsWith("{") || text.startsWith("[")
                    ? Optional.empty()
                    : usefulText(text);
        }
    }

    private static Optional<String> eventActivity(JsonNode event) {
        String type = text(event, "type");
        if (type == null || isInternalType(type)) {
            return Optional.empty();
        }

        if (isToolEvent(type, event)) {
            return toolActivity(event);
        }
        if (type.toLowerCase(Locale.ROOT).contains("test")) {
            String result = firstText(event, "result", "status", "message", "content");
            return Optional.of("Test completed: " + concise(result == null ? type : result));
        }
        if (type.toLowerCase(Locale.ROOT).contains("message")
                || type.toLowerCase(Locale.ROOT).contains("assistant")) {
            return usefulText(firstText(event, "message", "text", "content", "value"));
        }
        if (type.equalsIgnoreCase("result") || type.equalsIgnoreCase("completed")) {
            return Optional.of("Copilot completed.");
        }
        return Optional.empty();
    }

    private static Optional<String> toolActivity(JsonNode event) {
        String tool = firstTextDeep(event, "toolName", "name", "toolType");
        String path = firstTextDeep(event, "path", "filePath", "file", "filename");
        String command = firstTextDeep(event, "command", "cmd", "shellCommand");
        String normalizedTool = tool == null ? "" : tool.toLowerCase(Locale.ROOT);

        if (isTest(tool, command)) {
            return Optional.of(command == null
                    ? "Running tests..."
                    : "Running tests: " + concise(command));
        }
        if (isTerminal(normalizedTool) || command != null) {
            return Optional.of("Running " + concise(command == null ? tool : command) + "...");
        }
        if (isEdit(normalizedTool)) {
            return Optional.of(path == null ? "Editing files..." : "Editing " + concise(path) + "...");
        }
        if (isRead(normalizedTool)) {
            return Optional.of(path == null ? "Reading workspace..." : "Reading " + concise(path) + "...");
        }
        if (path != null) {
            return Optional.of("Changed " + concise(path));
        }
        return tool == null ? Optional.empty() : Optional.of(concise(tool) + "...");
    }

    private static boolean isToolEvent(String type, JsonNode event) {
        String lower = type.toLowerCase(Locale.ROOT);
        return lower.contains("tool") || event.has("toolName") || event.has("toolCallId");
    }

    private static boolean isInternalType(String type) {
        String lower = type.toLowerCase(Locale.ROOT);
        return lower.contains("delta")
                || lower.contains("session")
                || lower.contains("metadata")
                || lower.contains("call_finished")
                || lower.contains("message_id")
                || lower.contains("input")
                || lower.contains("output")
                || lower.contains("store")
                || lower.equals("model.call_finished");
    }

    private static boolean isTerminal(String tool) {
        return tool.contains("bash") || tool.contains("shell") || tool.contains("terminal")
                || tool.contains("command") || tool.contains("exec") || tool.contains("run");
    }

    private static boolean isEdit(String tool) {
        return tool.contains("edit") || tool.contains("write") || tool.contains("patch")
                || tool.contains("create") || tool.contains("delete") || tool.contains("move");
    }

    private static boolean isRead(String tool) {
        return tool.contains("read") || tool.contains("inspect") || tool.contains("list")
                || tool.contains("search") || tool.contains("find");
    }

    private static boolean isTest(String tool, String command) {
        String value = ((tool == null ? "" : tool) + " " + (command == null ? "" : command))
                .toLowerCase(Locale.ROOT);
        return value.contains("test") || value.contains("mvn ") && value.contains(" test")
                || value.contains("gradle") && value.contains(" test");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstTextDeep(JsonNode node, String... fields) {
        String direct = firstText(node, fields);
        if (direct != null) {
            return direct;
        }
        if (node.isTextual() && node.asText().trim().startsWith("{")) {
            try {
                return firstTextDeep(JSON.readTree(node.asText()), fields);
            } catch (Exception ignored) {
                return null;
            }
        }
        Iterator<JsonNode> children = node.elements();
        while (children.hasNext()) {
            String nested = firstTextDeep(children.next(), fields);
            if (nested != null) {
                return nested;
            }
        }
        Iterator<Map.Entry<String, JsonNode>> properties = node.fields();
        while (properties.hasNext()) {
            String nested = firstTextDeep(properties.next().getValue(), fields);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static Optional<String> usefulText(String value) {
        if (value == null || value.isBlank() || value.trim().startsWith("{")) {
            return Optional.empty();
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("toolcallid") || lower.contains("session_store_sql")
                || lower.contains("inputdelta") || lower.contains("messageid")) {
            return Optional.empty();
        }
        return Optional.of(concise(value));
    }

    private static String concise(String value) {
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() > MAX_ACTIVITY_LENGTH
                ? compact.substring(0, MAX_ACTIVITY_LENGTH - 3) + "..."
                : compact;
    }
}
