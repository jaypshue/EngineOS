package com.engineos.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentProviderTest {
    @Test
    void exposesUserFacingNameForGitHubCopilot() {
        var provider = new AgentProvider("github", "copilot-cli");

        assertEquals("GitHub Copilot", provider.displayName());
        assertEquals("GitHub Copilot", provider.toString());
    }
}
