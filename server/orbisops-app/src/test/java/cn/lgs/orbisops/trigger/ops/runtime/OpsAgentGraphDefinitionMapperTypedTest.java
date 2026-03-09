package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowEdgeDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeDefinition;
import cn.lgs.orbisops.trigger.application.agentdefinition.AgentWorkflowDefinitionMigrator;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionSnapshotMapper;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeType;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowResourceReference;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowRouteMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAgentGraphDefinitionMapperTypedTest {

    private final OpsAgentGraphDefinitionMapper mapper =
            new OpsAgentGraphDefinitionMapper();

    @Test
    void inboundNodeMustBecomeTypedDefinitionWithAllResourceReferences() {
        OpsWorkflowNode node = OpsWorkflowNode.builder()
                .nodeId("worker")
                .type("tool_call")
                .mode("REACT")
                .agent("tool-agent")
                .description("invoke a governed tool")
                .instruction("collect evidence")
                .modelId("model-1")
                .knowledgeBaseId("kb-1")
                .skills(List.of("skill-a"))
                .mcpIds(List.of("mcp-a"))
                .executionTargetIds(List.of("target-a"))
                .mcpServers(List.of(OpsMcpServerConfig.builder().name("inline-a").build()))
                .config(Map.of("inheritProjectCapabilities", false))
                .build();

        AgentWorkflowNodeDefinition typed = mapper.workflowNode(node);

        assertEquals(AgentWorkflowNodeType.TOOL, typed.nodeType());
        assertEquals("TOOL_CALL", typed.publishedType());
        assertEquals("invoke a governed tool", typed.description());
        assertEquals(6, typed.resources().size());
        assertTrue(typed.references(AgentWorkflowResourceReference.ResourceType.MODEL));
        assertTrue(typed.references(AgentWorkflowResourceReference.ResourceType.KNOWLEDGE_BASE));
        assertTrue(typed.references(AgentWorkflowResourceReference.ResourceType.SKILL));
        assertTrue(typed.references(AgentWorkflowResourceReference.ResourceType.MCP));
        assertTrue(typed.references(AgentWorkflowResourceReference.ResourceType.EXECUTION_TARGET));
        assertTrue(typed.references(AgentWorkflowResourceReference.ResourceType.INLINE_MCP));
    }

    @Test
    void versionedEnvelopeMustReuseExistingVersionAndCanonicalDefinitionHash() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("agent-1")
                .schemaVersion(1)
                .version(7)
                .nodes(List.of(OpsWorkflowNode.builder()
                        .nodeId("tool")
                        .type("TOOL_CALL")
                        .agent("tool-agent")
                        .build()))
                .build();
        new AgentWorkflowDefinitionMigrator().migrate(definition);
        OpsAgentDefinitionSnapshotMapper snapshotMapper =
                new OpsAgentDefinitionSnapshotMapper();
        String hashWithSchema = snapshotMapper.definitionHash(definition);
        definition.setDefinitionHash(hashWithSchema);

        AgentWorkflowDefinition typed = mapper.workflowDefinition(definition);

        assertEquals(1, typed.schemaVersion());
        assertEquals(7, typed.definitionVersion());
        assertEquals(hashWithSchema, typed.definitionHash());
        assertEquals("agent-1", typed.graph().agentId());

        definition.setSchemaVersion(null);
        String legacyHash = snapshotMapper.definitionHash(definition);
        assertNotEquals(hashWithSchema, legacyHash);
    }

    @Test
    void inboundEdgeMustPreserveRoutingRulePriorityAndDataMapping() {
        OpsGraphEdge edge = OpsGraphEdge.builder()
                .edgeId("edge-1")
                .name("approved")
                .from("review")
                .to("execute")
                .conditionType("review_decision")
                .condition("APPROVED")
                .priority(20)
                .feedback(false)
                .dataMapping(Map.of("review", "approval"))
                .description("continue after approval")
                .build();

        AgentWorkflowEdgeDefinition typed = mapper.workflowEdge(edge);

        assertEquals("edge-1", typed.edgeId());
        assertEquals(AgentWorkflowRouteMode.REVIEW_DECISION, typed.routeMode());
        assertEquals("APPROVED", typed.rule().expression());
        assertEquals(20, typed.priority());
        assertEquals("approval", typed.dataMapping().get("review"));
        assertEquals("continue after approval", typed.description());
    }
}
