package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityBinding;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityBindingSnapshot;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityOwnerType;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityScope;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityType;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionLifecycle;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentScopeConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpServerConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkflowNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAgentCapabilityBindingMapperTest {

    @Test
    void mapsAllOwnerTypesAndDomainCapabilityScopes() {
        OpsMcpServerConfig inline = OpsMcpServerConfig.builder()
                .name("inline-query")
                .transport(null)
                .allowedTools(List.of("query"))
                .toolCapabilities(Map.of("query", "read"))
                .build();
        OpsWorkflowNode node = OpsWorkflowNode.builder()
                .nodeId("diagnose")
                .skills(List.of("node-skill"))
                .mcpIds(List.of("node-tool"))
                .executionTargetIds(List.of("node-target"))
                .knowledgeBaseId("node-kb")
                .mcpServers(List.of(inline))
                .build();
        OpsAgentScopeConfig scope = OpsAgentScopeConfig.builder()
                .agentId(null)
                .skills(List.of("scope-skill"))
                .mcpIds(List.of("scope-tool"))
                .executionTargetIds(List.of("scope-target"))
                .knowledgeBaseId("scope-kb")
                .mcpServers(List.of(inline))
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("agent-a")
                .version(3)
                .lifecycle("PUBLISHED")
                .projectId("project-a")
                .skills(List.of("agent-skill", " "))
                .mcpIds(List.of("agent-tool"))
                .executionTargetIds(List.of("agent-target"))
                .knowledgeBaseId("agent-kb")
                .mcpServers(List.of(inline))
                .nodes(List.of(node))
                .agentscopeAgents(List.of(scope))
                .build();

        AgentCapabilityBindingSnapshot snapshot = new OpsAgentCapabilityBindingMapper().map(definition);

        assertEquals("agent-a", snapshot.agentId());
        assertEquals(3, snapshot.version());
        assertEquals(AgentDefinitionLifecycle.PUBLISHED, snapshot.lifecycle());
        assertEquals(15, snapshot.bindings().size());
        assertTrue(snapshot.bindings().stream().anyMatch(binding ->
                binding.ownerType() == AgentCapabilityOwnerType.AGENTSCOPE
                        && "agentscope_0".equals(binding.nodeId())));
        assertTrue(snapshot.bindings().stream().anyMatch(binding ->
                binding.capabilityType() == AgentCapabilityType.INLINE_MCP_SERVER
                        && binding.capabilityScope() == AgentCapabilityScope.INLINE
                        && "stdio".equals(binding.bindConfig().get("transport"))));
        assertTrue(snapshot.bindings().stream().anyMatch(binding ->
                binding.capabilityType() == AgentCapabilityType.PROJECT_TOOL
                        && binding.capabilityScope() == AgentCapabilityScope.PROJECT));
        assertTrue(snapshot.bindings().stream().anyMatch(binding ->
                binding.capabilityType() == AgentCapabilityType.SKILL
                        && binding.capabilityScope() == AgentCapabilityScope.PROJECT_OR_ENABLED_GLOBAL));
    }

    @Test
    void validationMappingMustNotWeakenPersistentIdentityRequirement() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .projectId("project-a")
                .skills(List.of("project-skill"))
                .build();
        OpsAgentCapabilityBindingMapper mapper = new OpsAgentCapabilityBindingMapper();

        assertThrows(IllegalArgumentException.class, () -> mapper.map(definition));
        AgentCapabilityBindingSnapshot snapshot = mapper.mapForValidation(definition);

        assertEquals("__capability_validation__", snapshot.agentId());
        assertEquals(1, snapshot.bindings().size());
        assertEquals("project-skill", snapshot.bindings().get(0).capabilityId());
    }

    @Test
    void mapsTypedBindingBackToLegacyApiShape() {
        AgentCapabilityBinding binding = new AgentCapabilityBinding(
                7L,
                "agent-a",
                3,
                AgentDefinitionLifecycle.PUBLISHED,
                "project-a",
                AgentCapabilityOwnerType.NODE,
                "diagnose",
                AgentCapabilityType.INLINE_MCP_SERVER,
                "inline-query",
                AgentCapabilityScope.INLINE,
                Map.of("transport", "http"),
                "system",
                Instant.parse("2026-07-21T00:00:00Z"));

        Map<String, Object> view = new OpsAgentCapabilityBindingMapper()
                .views(List.of(binding))
                .get(0);

        assertEquals("inline_mcp_server", view.get("capabilityType"));
        assertEquals("INLINE", view.get("capabilityScope"));
        assertEquals("{\"transport\":\"http\"}", view.get("bindConfigJson"));
        assertEquals("diagnose", view.get("nodeId"));

        Map<String, Object> compatibilityView = new OpsAgentCapabilityBindingMapper()
                .compatibilityViews(List.of(binding))
                .get(0);
        assertEquals(Map.of("transport", "http"), compatibilityView.get("bindConfig"));
        assertTrue(!compatibilityView.containsKey("bindConfigJson"));
        assertTrue(!compatibilityView.containsKey("version"));
    }
}
