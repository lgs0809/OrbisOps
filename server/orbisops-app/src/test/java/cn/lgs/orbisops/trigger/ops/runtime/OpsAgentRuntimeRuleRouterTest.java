package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAgentRuntimeRuleRouterTest {

    private final OpsAgentRuntimeRuleRouter router = new OpsAgentRuntimeRuleRouter();

    @Test
    void reactAndWorkflowShareOneStateGraphInfrastructureAdapter() {
        for (String mode : List.of("SIMPLE", "MULTI_TURN", "AGENT", "WORKFLOW")) {
            OpsRuntimeExecutionPlan plan = router.plan(
                    OpsAgentChatRequest.builder().mode(mode).engine("CHAT").build(),
                    OpsAgentDefinition.builder().agentId("agent-1").build());

            assertEquals(OpsUnifiedAgentEngineAdapter.KEY, plan.getAdapterKey());
            assertEquals("STATE_GRAPH", plan.getEngine());
        }
    }

    @Test
    void simpleAndMultiTurnRemainReactPoliciesNotSeparateEngines() {
        OpsRuntimeExecutionPlan simple = router.plan(
                OpsAgentChatRequest.builder().mode("SIMPLE").build(),
                OpsAgentDefinition.builder().agentId("agent-1").build());
        OpsRuntimeExecutionPlan multiTurn = router.plan(
                OpsAgentChatRequest.builder().mode("MULTI_TURN").build(),
                OpsAgentDefinition.builder().agentId("agent-1").build());

        assertFalse(simple.isMemoryEnabled());
        assertTrue(multiTurn.isMemoryEnabled());
        assertEquals("REACT", simple.getMetadata().get("executionStyle"));
        assertEquals("REACT", multiTurn.getMetadata().get("executionStyle"));
    }

    @Test
    void explicitMemoryOverrideDoesNotChangeExecutionStyle() {
        OpsRuntimeExecutionPlan disabled = router.plan(
                OpsAgentChatRequest.builder().mode("AGENT").memoryEnabled(false).build(),
                OpsAgentDefinition.builder().agentId("agent-1").build());
        OpsRuntimeExecutionPlan enabled = router.plan(
                OpsAgentChatRequest.builder().mode("SIMPLE").memoryEnabled(true).build(),
                OpsAgentDefinition.builder().agentId("agent-1").build());

        assertFalse(disabled.isMemoryEnabled());
        assertTrue(enabled.isMemoryEnabled());
        assertEquals("REACT", disabled.getMetadata().get("executionStyle"));
        assertEquals("REACT", enabled.getMetadata().get("executionStyle"));
        assertEquals(false, disabled.getMetadata().get("memoryEnabled"));
        assertEquals(true, enabled.getMetadata().get("memoryEnabled"));
    }

    @Test
    void manuallySelectedDragDropDefinitionIsFixedWorkflowNotReactHybrid() {
        OpsAgentDefinition visual = OpsAgentDefinition.builder()
                .agentId("visual-workflow")
                .nodes(List.of(
                        OpsWorkflowNode.builder().nodeId("llm").type("CHAT").build(),
                        OpsWorkflowNode.builder().nodeId("tool").type("TOOL").build()))
                .build();

        OpsRuntimeExecutionPlan plan = router.plan(
                OpsAgentChatRequest.builder().mode("WORKFLOW").build(),
                visual);

        assertEquals(OpsUnifiedAgentEngineAdapter.KEY, plan.getAdapterKey());
        assertFalse(plan.isHybrid());
        assertEquals("WORKFLOW", plan.getMetadata().get("executionStyle"));
        assertTrue(plan.getReason().contains("fixed graph topology"));
    }
}
