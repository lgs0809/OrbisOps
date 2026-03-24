package cn.lgs.orbisops.domain.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.SubAgentToolBoundaryDecision;
import cn.lgs.orbisops.domain.agentdefinition.service.SubAgentToolBoundaryPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubAgentToolBoundaryPolicyTest {

    private final SubAgentToolBoundaryPolicy policy = new SubAgentToolBoundaryPolicy();

    @Test
    void inactiveWhenNoBuiltInRoleWasDeclared() {
        SubAgentToolBoundaryDecision decision = policy.evaluate(
                "",
                null,
                List.of(),
                List.of("code_read"));

        assertFalse(decision.active());
        assertEquals(List.of(), decision.permittedToolNames());
    }

    @Test
    void appliesDefaultRoleBoundaryAndSuffixWildcard() {
        SubAgentToolBoundaryDecision decision = policy.evaluate(
                "evidence-explorer",
                1,
                List.of(),
                List.of(
                        "Skill",
                        "project_mcp_search_logs",
                        "prometheus_query",
                        "elasticsearch_search",
                        "tool_result_1",
                        "code_write"));

        assertTrue(decision.active());
        assertEquals("EVIDENCE_EXPLORER", decision.role());
        assertEquals(1, decision.maxDepth());
        assertEquals(
                List.of("Skill", "project_mcp_search_logs", "prometheus_query", "elasticsearch_search", "tool_result_1"),
                decision.permittedToolNames());
    }

    @Test
    void generalRoleMustKeepMainAssistantChangePackageCapability() {
        SubAgentToolBoundaryDecision decision = policy.evaluate(
                "general",
                1,
                List.of(),
                List.of("prometheus_query", "PrepareChangePackage", "QueryChangePackageStatus", "code_read", "change_package_approve", "unsafe_admin"));

        assertEquals("MAIN_ASSISTANT", decision.role());
        assertEquals(
                List.of("prometheus_query", "PrepareChangePackage", "QueryChangePackageStatus", "code_read"),
                decision.permittedToolNames());
    }

    @Test
    void configuredAllowlistOverridesRoleDefaults() {
        SubAgentToolBoundaryDecision decision = policy.evaluate(
                "repair-worker",
                1,
                List.of("code_read", "code_grep"),
                List.of("code_read", "code_write", "code_grep"));

        assertEquals(List.of("code_read", "code_grep"), decision.permittedToolNames());
    }

    @Test
    void rejectsRecursiveBuiltInSubAgentDepth() {
        SecurityException error = assertThrows(
                SecurityException.class,
                () -> policy.evaluate(
                        "evidence-critic",
                        2,
                        List.of(),
                        List.of()));

        assertEquals(
                "SUB_AGENT_DEPTH_EXCEEDED：内置子 Agent 最大深度必须为 1",
                error.getMessage());
    }
}
