package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkflowNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentWorkflowDefinitionMigratorTest {

    private final AgentWorkflowDefinitionMigrator migrator =
            new AgentWorkflowDefinitionMigrator();

    @Test
    void legacyDefinitionMustMigrateWithExistingNormalizationSemantics() {
        OpsWorkflowNode plan = OpsWorkflowNode.builder()
                .nodeId("plan")
                .type("PLAN")
                .agent("planner")
                .build();
        OpsWorkflowNode router = OpsWorkflowNode.builder()
                .nodeId("router")
                .type("ROUTER")
                .agent("router")
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("legacy")
                .nodes(List.of(plan, router))
                .build();

        migrator.migrate(definition);

        assertEquals(AgentWorkflowDefinition.CURRENT_SCHEMA_VERSION,
                definition.getSchemaVersion());
        assertEquals("AGENT", plan.getType());
        assertEquals("plan", plan.getMode());
        assertEquals("main_planner", plan.getConfig().get("role"));
        assertEquals("ROUTER", router.getType());
        assertEquals("selectedRoutes", router.getOutputKey());
        assertEquals("multi", router.getConfig().get("routeMode"));
    }

    @Test
    void legacyV0MustPreserveCurrentDirectAndLlmExecutionModes() {
        OpsWorkflowNode direct = OpsWorkflowNode.builder()
                .nodeId("direct")
                .type("AGENT")
                .mode("direct")
                .agent("direct-agent")
                .config(Map.of("mode", "direct"))
                .build();
        OpsWorkflowNode llm = OpsWorkflowNode.builder()
                .nodeId("llm")
                .type("AGENT")
                .mode("llm")
                .agent("llm-agent")
                .config(Map.of("mode", "llm", "role", "reviewer"))
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("legacy-current-modes")
                .nodes(List.of(direct, llm))
                .build();

        migrator.migrate(definition);

        assertEquals("direct", direct.getMode());
        assertEquals("direct", direct.getConfig().get("mode"));
        assertEquals("llm", llm.getMode());
        assertEquals("llm", llm.getConfig().get("mode"));
        assertEquals("reviewer", llm.getConfig().get("role"));
    }

    @Test
    void schemaV1MustPreserveTypedPublishedNodeKinds() {
        OpsWorkflowNode tool = OpsWorkflowNode.builder()
                .nodeId("tool")
                .type("tool_call")
                .agent("tool-agent")
                .build();
        OpsWorkflowNode parallel = OpsWorkflowNode.builder()
                .nodeId("parallel")
                .type("PARALLEL")
                .agent("parallel-agent")
                .config(Map.of())
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("typed")
                .schemaVersion(1)
                .nodes(List.of(tool, parallel))
                .build();

        migrator.migrate(definition);

        assertEquals("TOOL_CALL", tool.getType());
        assertEquals("react", tool.getMode());
        assertEquals("PARALLEL", parallel.getType());
    }

    @Test
    void futureSchemaMustFailClosedWithoutPartialMigration() {
        OpsWorkflowNode node = OpsWorkflowNode.builder()
                .nodeId("tool")
                .type("tool_call")
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("future")
                .schemaVersion(2)
                .nodes(List.of(node))
                .build();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> migrator.migrate(definition));

        assertEquals("WORKFLOW_SCHEMA_VERSION_UNSUPPORTED:2", error.getMessage());
        assertEquals("tool_call", node.getType());
    }
}
