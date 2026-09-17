package com.engineos.agent;

import com.engineos.domain.CodingResult;
import com.engineos.domain.CodingTask;
import com.engineos.domain.WorkspaceContext;

public interface CoderAgent {
    AgentProvider provider();

    CodingResult execute(WorkspaceContext workspace, CodingTask task);
}
