package com.engineos.agent;

import com.engineos.domain.CopilotResultDetails;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CopilotResultParser {
    private CopilotResultParser() {
    }

    static CopilotResultDetails parse(String output) {
        if (output == null || output.isBlank()) {
            return CopilotResultDetails.unavailable();
        }

        CopilotResultDetails found = CopilotResultDetails.unavailable();
        for (String line : output.split("\\R")) {
            try {
                Object value = new JsonParser(line).parse();
                if (!(value instanceof Map<?, ?> object)) {
                    continue;
                }
                Object type = object.get("type");
                if ("result".equals(type) || object.containsKey("codeChanges")) {
                    found = details(object);
                }
            } catch (RuntimeException ignored) {
                // Preserve the raw output and continue looking for a valid JSONL event.
            }
        }
        return found;
    }

    static AiUsage parseUsage(String output, String requestedModel) {
        if (output == null || output.isBlank()) {
            return AiUsage.unavailable();
        }
        AiUsage found = AiUsage.unavailable();
        for (String line : output.split("\\R")) {
            try {
                Object value = new JsonParser(line).parse();
                if (!(value instanceof Map<?, ?> event) || !(event.get("usage") instanceof Map<?, ?>)) {
                    continue;
                }
                Map<?, ?> usage = object(event.get("usage"));
                String model = string(usage.get("currentModel"));
                if (model == null) {
                    model = string(usage.get("model"));
                }
                if (model == null) {
                    model = string(event.get("model"));
                }
                String credits = decimal(usage.get("totalNanoAiu"));
                found = new AiUsage(
                        model == null ? requestedModel : model,
                        credits,
                        text(usage.get("inputTokens")),
                        text(usage.get("outputTokens")),
                        text(usage.get("cacheReadTokens")),
                        text(usage.get("reasoningTokens")),
                        text(usage.get("quotaUsed")),
                        text(usage.get("quotaRemaining")));
            } catch (RuntimeException ignored) {
                // Preserve unavailable usage when an individual JSONL event is malformed.
            }
        }
        return found;
    }

    private static CopilotResultDetails details(Map<?, ?> event) {
        Map<?, ?> changes = object(event.get("codeChanges"));
        List<String> files = strings(changes.get("filesModified"));
        Integer fileCount = integer(changes.get("filesModified"));
        if (fileCount == null && changes.get("filesModified") instanceof List<?>) {
            fileCount = files.size();
        }
        String actualModel = string(event.get("model"));
        if (actualModel == null) {
            actualModel = string(event.get("actualModel"));
        }
        if (actualModel == null) {
            Map<?, ?> usage = object(event.get("usage"));
            actualModel = string(usage.get("currentModel"));
            if (actualModel == null) {
                actualModel = string(usage.get("model"));
            }
        }
        return new CopilotResultDetails(
                true,
                actualModel,
                number(event, "totalApiDurationMs", "totalApiDuration"),
                number(event, "sessionDurationMs", "sessionDuration", "durationMs"),
                integer(changes.get("linesAdded")),
                integer(changes.get("linesRemoved")),
                fileCount,
                files);
    }

    private static Map<?, ?> object(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private static String string(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private static Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String decimal(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        if (number.doubleValue() <= 0) {
            return null;
        }
        return String.format("%.4f", number.doubleValue() / 1_000_000_000d);
    }

    private static Long number(Map<?, ?> object, String... keys) {
        for (String key : keys) {
            Object value = object.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
        }
        return null;
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        var result = new ArrayList<String>();
        for (Object item : values) {
            if (item instanceof String text) {
                result.add(text);
            }
        }
        return result;
    }

    private static final class JsonParser {
        private final String text;
        private int index;

        private JsonParser(String text) {
            this.text = text.trim();
        }

        private Object parse() {
            skipWhitespace();
            Object value = value();
            skipWhitespace();
            if (index != text.length()) {
                throw new IllegalArgumentException("Trailing JSON content");
            }
            return value;
        }

        private Object value() {
            skipWhitespace();
            if (index >= text.length()) {
                throw new IllegalArgumentException("Missing JSON value");
            }
            return switch (text.charAt(index)) {
                case '{' -> objectValue();
                case '[' -> arrayValue();
                case '"' -> stringValue();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> numberValue();
            };
        }

        private Map<String, Object> objectValue() {
            expect('{');
            var result = new LinkedHashMap<String, Object>();
            skipWhitespace();
            if (peek('}')) {
                index++;
                return result;
            }
            while (true) {
                skipWhitespace();
                String key = stringValue();
                skipWhitespace();
                expect(':');
                result.put(key, value());
                skipWhitespace();
                if (peek('}')) {
                    index++;
                    return result;
                }
                expect(',');
            }
        }

        private List<Object> arrayValue() {
            expect('[');
            var result = new ArrayList<Object>();
            skipWhitespace();
            if (peek(']')) {
                index++;
                return result;
            }
            while (true) {
                result.add(value());
                skipWhitespace();
                if (peek(']')) {
                    index++;
                    return result;
                }
                expect(',');
            }
        }

        private String stringValue() {
            expect('"');
            var result = new StringBuilder();
            while (index < text.length()) {
                char current = text.charAt(index++);
                if (current == '"') {
                    return result.toString();
                }
                if (current == '\\') {
                    if (index >= text.length()) {
                        throw new IllegalArgumentException("Invalid escape");
                    }
                    char escaped = text.charAt(index++);
                    result.append(switch (escaped) {
                        case '"', '\\', '/' -> escaped;
                        case 'b' -> '\b';
                        case 'f' -> '\f';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        default -> throw new IllegalArgumentException("Unsupported escape");
                    });
                } else {
                    result.append(current);
                }
            }
            throw new IllegalArgumentException("Unterminated string");
        }

        private Number numberValue() {
            int start = index;
            while (index < text.length() && "-+0123456789.eE".indexOf(text.charAt(index)) >= 0) {
                index++;
            }
            String value = text.substring(start, index);
            if (value.contains(".") || value.contains("e") || value.contains("E")) {
                return Double.parseDouble(value);
            }
            return Long.parseLong(value);
        }

        private Object literal(String expected, Object value) {
            if (!text.startsWith(expected, index)) {
                throw new IllegalArgumentException("Invalid literal");
            }
            index += expected.length();
            return value;
        }

        private void expect(char expected) {
            skipWhitespace();
            if (index >= text.length() || text.charAt(index++) != expected) {
                throw new IllegalArgumentException("Expected " + expected);
            }
        }

        private boolean peek(char value) {
            return index < text.length() && text.charAt(index) == value;
        }

        private void skipWhitespace() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }
    }
}
