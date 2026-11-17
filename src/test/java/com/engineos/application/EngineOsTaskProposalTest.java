package com.engineos.application;

import com.engineos.domain.EngineeringTask;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EngineOsTaskProposalTest {
    @Test
    void preservesRulesEvidenceInProposalDto() {
        var proposal = new EngineOsService.TaskProposal(
                "request", "task", "title", "goal", "scope", "constraints", "rationale",
                "allowed", EngineeringTask.Status.PROPOSED, "out", List.of("accept"),
                "verify", "source", false, ".engineos/rules.md", "deadbeef", "Keep tests green.",
                "Keep tests green.");

        assertEquals(".engineos/rules.md", proposal.rulesPath());
        assertEquals("deadbeef", proposal.rulesSha256());
        assertEquals("Keep tests green.", proposal.rulesSummary());
        assertEquals("Keep tests green.", proposal.rulesContent());
    }
}
