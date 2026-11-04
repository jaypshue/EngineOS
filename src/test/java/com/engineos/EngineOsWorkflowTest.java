package com.engineos;

import com.engineos.agent.*;
import com.engineos.application.EngineOsWorkflow;
import com.engineos.domain.EngineeringModel;
import com.engineos.domain.EngineeringTask;
import com.engineos.infrastructure.RepositoryDiscovery;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EngineOsWorkflowTest {
    @Test
    void verticalSliceKeepsEngineerAndCoderBoundariesExplicit() {
        var workflow = new EngineOsWorkflow(
                new RepositoryDiscovery(),
                new DeterministicEngineerAgent(new AgentProvider("engineer-provider", "reasoning-model")),
                new LocalCoderAgent(new AgentProvider("coder-provider", "implementation-model")));
        var workspace = workflow.loadWorkspace(Path.of("."));
        var model = new EngineeringModel("Inspect this Java workspace");

        var task = workflow.receiveIntent(workspace, model.humanIntent(), model);

        assertEquals(EngineeringTask.Status.PROPOSED, task.status());
        assertEquals("engineer-provider", ((DeterministicEngineerAgent) new DeterministicEngineerAgent(
                new AgentProvider("engineer-provider", "reasoning-model"))).provider().providerId());
        assertThrows(IllegalStateException.class,
                () -> workflow.executeApproved(workspace, task, model, List.of("git", "status")));
        assertEquals(EngineeringTask.Status.PROPOSED, task.status());

        task.approve();
        var result = workflow.executeApproved(
                workspace, task, model, List.of("git", "status", "--short", "--branch"));

        assertTrue(result.succeeded());
        assertEquals(EngineeringTask.Status.COMPLETED, task.status());
        assertEquals(1, model.evidence().size());
        assertEquals(1, model.investigations().getFirst().evidence().size());
        assertTrue(model.conversation().stream().anyMatch(line -> line.startsWith("Engineer Agent:")));
    }
}
