package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class OpsAgentScopeExecutorTest {

    private final OpsAgentScopeExecutor executor = new OpsAgentScopeExecutor(
            mock(OpsRuntimeResourceAssembler.class),
            mock(OpsNodeRagService.class),
            new OpsRuntimePromptAssembler(),
            directExecutor());

    @Test
    void projectsAgentScopeConfigurationFromWorkflowNodes() {
        OpsWorkflowNode node = OpsWorkflowNode.builder()
                .nodeId("log-investigation")
                .type("AGENTSCOPE")
                .agent("log-agent")
                .instruction("inspect logs")
                .outputKey("log_output")
                .ragEnabled(true)
                .knowledgeBaseId("kb-1")
                .config(Map.of(
                        "role", "data_agent",
                        "maxDepth", 2,
                        "allowedToolNames", List.of("search_logs", "get_schema"),
                        "inheritProjectCapabilities", true))
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops-agent")
                .instruction("global instruction")
                .nodes(List.of(node))
                .build();

        List<OpsAgentScopeConfig> configs = executor.agentScopeConfigs(
                definition, new OpsAgentChatRequest());

        assertEquals(1, configs.size());
        OpsAgentScopeConfig config = configs.get(0);
        assertEquals("log-agent", config.getAgentId());
        assertEquals("log_output", config.getOutputKey());
        assertEquals("data_agent", config.getRole());
        assertEquals(2, config.getMaxDepth());
        assertEquals(List.of("search_logs", "get_schema"), config.getAllowedToolNames());
        assertTrue(config.getInheritProjectCapabilities());
        assertTrue(config.getRagEnabled());
        assertEquals("kb-1", config.getKnowledgeBaseId());
    }

    @Test
    void extractsConfiguredAndFallbackOutputsFromGraphState() {
        assertEquals("agent answer", executor.stateOutput(
                new OverAllState(Map.of("agent_0", "agent answer")), "agent_0"));
        assertEquals("fallback answer", executor.stateOutput(
                new OverAllState(Map.of("output", "fallback answer")), "missing"));
        assertEquals("", executor.stateOutput(new OverAllState(Map.of()), "missing"));
    }

    @Test
    void rejectsNullLikeAgentOutputs() {
        assertFalse(executor.isMeaningfulText(null));
        assertFalse(executor.isMeaningfulText("null"));
        assertFalse(executor.isMeaningfulText("[]"));
        assertFalse(executor.isMeaningfulText("{}"));
        assertTrue(executor.isMeaningfulText("evidence found"));
    }

    @Test
    void legacyRuntimeNoLongerOwnsAgentScopeSubsystemImplementations() {
        Set<String> migratedMethods = Set.of(
                "agentScopeConfigs",
                "selectBuiltInMicrokernelRoles",
                "buildAgentScopeFlow",
                "agentScopeMode",
                "agentMaxToolRounds",
                "agentRecursionLimit",
                "agentScopeStateOutput",
                "messageDiagnostics");

        assertFalse(Arrays.stream(OpsWorkSessionLifecycleCoordinator.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .anyMatch(migratedMethods::contains));
    }

    private Executor directExecutor() {
        return Runnable::run;
    }
}
