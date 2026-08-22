package cn.lgs.orbisops.domain.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentExecutionNodeFact;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentExecutionShapeDecision;
import cn.lgs.orbisops.domain.agentdefinition.service.AgentExecutionShapePolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentExecutionShapePolicyTest {

    private final AgentExecutionShapePolicy policy = new AgentExecutionShapePolicy();

    @Test
    void plainGraphNodesUseGraphEngine() {
        AgentExecutionShapeDecision decision = policy.decide(List.of(
                new AgentExecutionNodeFact("START", "", ""),
                new AgentExecutionNodeFact("AGENT", "AUTO", ""),
                new AgentExecutionNodeFact("END", "", "")));

        assertEquals("GRAPH", decision.engine());
        assertTrue(decision.clearLegacyAgentScope());
    }

    @Test
    void reactModeOrAgentScopeExecutionUsesHybridEngine() {
        assertEquals(
                "HYBRID",
                policy.decide(List.of(
                        new AgentExecutionNodeFact("AGENT", "react", "")))
                        .engine());
        assertEquals(
                "HYBRID",
                policy.decide(List.of(
                        new AgentExecutionNodeFact("custom", "", "agentscope")))
                        .engine());
        assertEquals(
                "HYBRID",
                policy.decide(List.of(
                        new AgentExecutionNodeFact("agentscope", "", "")))
                        .engine());
    }

    @Test
    void emptyDefinitionRemainsStandaloneReactAgent() {
        AgentExecutionShapeDecision decision = policy.decide(null);

        assertEquals("AGENTSCOPE", decision.engine());
        assertEquals(false, decision.clearLegacyAgentScope());
    }
}
