package com.engineos.agent;

public record AgentProvider(String providerId, String modelId) {
    public AgentProvider {
        if (providerId == null || providerId.isBlank() || modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("Agent provider and model identifiers are required");
        }
    }

    public String displayName() {
        return switch (providerId) {
            case "github" -> "GitHub Copilot";
            case "local" -> "Local Coding Agent";
            default -> providerId;
        };
    }

    @Override
    public String toString() {
        return displayName();
    }
}
