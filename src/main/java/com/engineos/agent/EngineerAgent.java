package com.engineos.agent;

import com.engineos.domain.*;

public interface EngineerAgent {
    AgentProvider provider();

    EngineeringTask proposeTask(WorkspaceContext workspace, EngineeringModel model);

    String review(Evidence evidence, EngineeringModel model);
}
