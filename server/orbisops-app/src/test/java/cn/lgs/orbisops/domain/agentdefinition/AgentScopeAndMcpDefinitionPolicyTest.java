package cn.lgs.orbisops.domain.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentMcpServerDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentScopeDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.OpsBuiltinSubAgentRole;
import cn.lgs.orbisops.domain.agentdefinition.service.AgentMcpServerDefinitionPolicy;
import cn.lgs.orbisops.domain.agentdefinition.service.AgentScopeDefinitionPolicy;
import cn.lgs.orbisops.domain.agentdefinition.service.AgentToolNamePolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentScopeAndMcpDefinitionPolicyTest {

    private final AgentToolNamePolicy toolNamePolicy = new AgentToolNamePolicy();
    private final AgentScopeDefinitionPolicy scopePolicy =
            new AgentScopeDefinitionPolicy(toolNamePolicy);
    private final AgentMcpServerDefinitionPolicy mcpPolicy =
            new AgentMcpServerDefinitionPolicy(toolNamePolicy);

    @Test
    void builtInScopeUsesFailClosedDefaultToolBoundary() {
        AgentScopeDefinition definition = new AgentScopeDefinition(List.of(
                new AgentScopeDefinition.Scope(
                        "critic",
                        "Evidence Critic",
                        "Critique evidence only",
                        1,
                        "evidence-critic",
                        List.of())));

        assertDoesNotThrow(() -> scopePolicy.validate(definition));
        assertEquals(
                List.of("UseProjectSkill", "tool_result_*"),
                OpsBuiltinSubAgentRole.EVIDENCE_CRITIC.defaultAllowedTools());
    }

    @Test
    void scopeIdentityAndDepthAreBounded() {
        AgentScopeDefinition duplicated = new AgentScopeDefinition(List.of(
                new AgentScopeDefinition.Scope("worker", "one", "do work", 1, "", List.of()),
                new AgentScopeDefinition.Scope("worker", "two", "do work", 1, "", List.of())));
        assertEquals(
                "ReAct 子 Agent ID 重复：worker",
                assertThrows(IllegalArgumentException.class,
                        () -> scopePolicy.validate(duplicated)).getMessage());

        AgentScopeDefinition deep = new AgentScopeDefinition(List.of(
                new AgentScopeDefinition.Scope("worker", "one", "do work", 2, "", List.of())));
        assertEquals(
                "ReAct 子 Agent 最大深度必须为 1：worker",
                assertThrows(IllegalArgumentException.class,
                        () -> scopePolicy.validate(deep)).getMessage());
    }

    @Test
    void unknownBuiltInRoleFailsClosed() {
        AgentScopeDefinition definition = new AgentScopeDefinition(List.of(
                new AgentScopeDefinition.Scope(
                        "writer", "writer", "write", 1,
                        "production-writer", List.of())));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> scopePolicy.validate(definition));

        assertTrue(error.getMessage().contains("不支持的内置子 Agent 角色"));
    }

    @Test
    void stdioAndRemoteMcpRequireTheirOwnConnectionIdentity() {
        AgentMcpServerDefinition missingCommand = definition(new AgentMcpServerDefinition.Server(
                "local", "stdio", "", "", List.of(), List.of(), List.of(), Map.of()));
        assertEquals(
                "Agent MCP local 缺少 command",
                assertThrows(IllegalArgumentException.class,
                        () -> mcpPolicy.validate(missingCommand, "Agent")).getMessage());

        AgentMcpServerDefinition missingUrl = definition(new AgentMcpServerDefinition.Server(
                "remote", "streamable-http", "", "", List.of(), List.of(), List.of(), Map.of()));
        assertEquals(
                "Agent MCP remote 缺少 url",
                assertThrows(IllegalArgumentException.class,
                        () -> mcpPolicy.validate(missingUrl, "Agent")).getMessage());
    }

    @Test
    void mcpToolNamesAndCapabilitiesAreValidatedAsDomainLanguage() {
        AgentMcpServerDefinition invalidTool = definition(new AgentMcpServerDefinition.Server(
                "ops", "stdio", "node", "",
                List.of("bad tool"), List.of(), List.of(), Map.of()));
        assertEquals(
                "Agent MCP ops allowedTools 包含非法工具名：bad tool",
                assertThrows(IllegalArgumentException.class,
                        () -> mcpPolicy.validate(invalidTool, "Agent")).getMessage());

        AgentMcpServerDefinition invalidCapability = definition(new AgentMcpServerDefinition.Server(
                "ops", "stdio", "node", "",
                List.of("search_logs"), List.of(), List.of(),
                Map.of("search_logs", "super-power")));
        assertEquals(
                "Agent MCP ops tool capability 不支持：super-power",
                assertThrows(IllegalArgumentException.class,
                        () -> mcpPolicy.validate(invalidCapability, "Agent")).getMessage());
    }

    private AgentMcpServerDefinition definition(AgentMcpServerDefinition.Server server) {
        return new AgentMcpServerDefinition(List.of(server));
    }
}
