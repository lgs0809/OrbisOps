package cn.lgs.orbisops.domain.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.service.AgentReferenceSyntaxPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentReferenceSyntaxPolicyTest {

    private final AgentReferenceSyntaxPolicy policy = new AgentReferenceSyntaxPolicy();

    @Test
    void acceptsStableCapabilityAndResourceIdentifiers() {
        assertDoesNotThrow(() -> policy.validateSkill("ops.runbook-v2", "Agent"));
        assertDoesNotThrow(() -> policy.validateResourceId("project:mcp.search-v2", "Agent MCP"));
        assertDoesNotThrow(() -> policy.validateResourceId("", "Agent modelId"));
    }

    @Test
    void rejectsMalformedReferencesBeforeAnyExternalLookup() {
        assertEquals(
                "Agent 引用了非法 skill：ops runbook",
                assertThrows(IllegalArgumentException.class,
                        () -> policy.validateSkill("ops runbook", "Agent")).getMessage());
        assertEquals(
                "Agent MCP 引用了非法 ID：mcp/one",
                assertThrows(IllegalArgumentException.class,
                        () -> policy.validateResourceId("mcp/one", "Agent MCP")).getMessage());
    }
}
