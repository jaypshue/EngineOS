package com.engineos.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A small, immutable boundary declaration captured with the engineering request.
 * It supports path globs and simple free-text directory/package terms.
 */
public record RequestBoundary(String declaration, List<String> positiveTerms, List<String> negativeTerms) {
    public RequestBoundary {
        declaration = declaration == null ? "" : declaration.trim();
        positiveTerms = positiveTerms == null ? List.of() : List.copyOf(positiveTerms);
        negativeTerms = negativeTerms == null ? List.of() : List.copyOf(negativeTerms);
    }

    public static RequestBoundary from(EngineeringRequest request) {
        return fromDeclaration(request == null ? "" : request.sections().get("BOUNDARY"));
    }

    public static RequestBoundary fromDeclaration(String declaration) {
        String source = declaration == null ? "" : declaration.trim();
        if (source.isBlank()) {
            return new RequestBoundary("", List.of(), List.of());
        }
        List<String> positive = new ArrayList<>();
        List<String> negative = new ArrayList<>();
        for (String part : source.split("[,;\\r\\n]+")) {
            String value = part.trim();
            if (value.isBlank()) {
                continue;
            }
            String lower = value.toLowerCase(Locale.ROOT);
            boolean excluded = lower.contains("do not touch")
                    || lower.contains("don't touch")
                    || lower.startsWith("exclude ")
                    || lower.startsWith("outside ");
            String extracted = excluded
                    ? value.replaceFirst("(?i).*?(?:do not touch|don't touch|exclude|outside)\\s+", "")
                    : value;
            for (String token : extracted.split("\\s*\\+\\s*|\\s+and\\s+|\\s+or\\s+")) {
                String normalized = normalize(token);
                if (normalized.isBlank() || isFiller(normalized)) {
                    continue;
                }
                (excluded ? negative : positive).add(normalized);
            }
        }
        return new RequestBoundary(source, positive.stream().distinct().toList(), negative.stream().distinct().toList());
    }

    public boolean present() {
        return !declaration.isBlank();
    }

    public List<String> violations(List<String> paths) {
        if (!present()) {
            return List.of();
        }
        return (paths == null ? List.<String>of() : paths).stream()
                .filter(path -> path != null && !normalizePath(path).isBlank())
                .filter(path -> negativeTerms.stream().anyMatch(term -> matches(term, normalizePath(path)))
                        || (!positiveTerms.isEmpty()
                        && positiveTerms.stream().noneMatch(term -> matches(term, normalizePath(path)))))
                .distinct()
                .sorted()
                .toList();
    }

    private static boolean matches(String term, String path) {
        term = term.toLowerCase(Locale.ROOT);
        path = path.toLowerCase(Locale.ROOT);
        if (term.contains("*") || term.contains("?")) {
            return Pattern.compile(globRegex(term), Pattern.CASE_INSENSITIVE).matcher(path).matches();
        }
        return path.equals(term) || path.contains("/" + term + "/")
                || path.startsWith(term + "/") || path.endsWith("/" + term);
    }

    private static String normalize(String value) {
        String normalized = normalizePath(value)
                .replaceAll("(?i)^(?:path|paths|package|packages|module|modules)(?:\\s*[:=]|\\s+)", "")
                .replaceAll("(?i)\\s+only$", "")
                .trim();
        return normalized.contains(".") && !normalized.contains("/")
                ? normalized.replace('.', '/').toLowerCase(Locale.ROOT)
                : normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalizePath(String value) {
        return value == null ? "" : value.trim().replace('\\', '/')
                .replaceAll("^['\"]|['\"]$", "").replaceAll("/+", "/")
                .replaceFirst("^\\./", "");
    }

    private static boolean isFiller(String value) {
        return List.of("only", "scope", "boundary", "files", "file", "changes", "change").contains(value);
    }

    private static String globRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int index = 0; index < glob.length(); index++) {
            char current = glob.charAt(index);
            if (current == '*' && index + 1 < glob.length() && glob.charAt(index + 1) == '*') {
                regex.append(".*");
                index++;
            } else if (current == '*') {
                regex.append("[^/]*");
            } else if (current == '?') {
                regex.append("[^/]");
            } else {
                if ("\\.[]{}()+-^$|".indexOf(current) >= 0) {
                    regex.append('\\');
                }
                regex.append(current);
            }
        }
        return regex.append('$').toString();
    }
}
