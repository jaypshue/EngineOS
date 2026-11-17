package com.engineos.domain;

import java.nio.file.Path;

/** Immutable snapshot of the single EngineOS project rules file. */
public record ProjectRules(Path path, String content, String sha256) {
    public static final ProjectRules NONE = new ProjectRules(null, "", "");

    public ProjectRules {
        content = content == null ? "" : content;
        sha256 = sha256 == null ? "" : sha256;
    }

    public boolean present() {
        return path != null;
    }

    public String summary() {
        if (!present()) {
            return "No .engineos/rules.md loaded.";
        }
        String compact = content.replaceAll("\\s+", " ").trim();
        return compact.length() > 240 ? compact.substring(0, 237) + "..." : compact;
    }

    public String shortSha256() {
        return sha256.length() <= 12 ? sha256 : sha256.substring(0, 12);
    }
}
