package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.agentdefinition.compilation.CompiledAgentDefinitionVersion;
import cn.lgs.orbisops.domain.agentdefinition.compilation.CompiledWorkflowNode;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeType;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowResourceReference;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextBundleSnapshot;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowExecutionPlan;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowResourceKind;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class OpsRuntimeWorkflowBindingAdapterTest {

    @Test
    void adapterMustFreezeOnlyStableResourceFacts() {
        OpsRuntimeWorkflowBindingAdapter adapter = new OpsRuntimeWorkflowBindingAdapter();

        BoundWorkflowExecutionPlan plan = adapter.bind(
                compiled(), runtime("model-1"), contextBundle(),
                "session-1", "run-1", Instant.parse("2026-08-02T01:00:00Z"));

        assertEquals("definition-hash", plan.definitionHash());
        assertEquals("bundle-hash", plan.contextBundleHash());
        assertEquals(List.of(
                        BoundWorkflowResourceKind.KNOWLEDGE_BASE,
                        BoundWorkflowResourceKind.MCP,
                        BoundWorkflowResourceKind.MODEL,
                        BoundWorkflowResourceKind.SKILL),
                plan.nodes().get(0).resources().stream().map(resource -> resource.kind()).toList());
        assertEquals(9, plan.completedBindingStages().size());
        assertTrue(plan.planHash().matches("[0-9a-f]{64}"));
        assertTrue(plan.allResources().stream().noneMatch(resource ->
                resource.resourceHash().contains("secret") || resource.bindingSource().contains("http")));
    }

    @Test
    void nodeSpecificResourcesMustBindBeforeGlobalFallback() {
        OpsRuntimeWorkflowBindingAdapter adapter = new OpsRuntimeWorkflowBindingAdapter();
        OpsRuntimeResourceBundle global = OpsRuntimeResourceBundle.builder()
                .agentVersion(7)
                .projectId("project-1")
                .modelId("other-model")
                .metadata(Map.of("modelVersion", "global"))
                .build();

        BoundWorkflowExecutionPlan plan = adapter.bind(
                compiled(),
                global,
                Map.of("start", runtime("model-1")),
                contextBundle(),
                "session-1",
                "run-1",
                Instant.parse("2026-08-02T01:00:00Z"));

        assertEquals(4, plan.nodes().get(0).resources().size());
        assertTrue(plan.nodes().get(0).resources().stream().anyMatch(resource ->
                resource.kind() == BoundWorkflowResourceKind.MCP && "mcp-1".equals(resource.resourceId())));
    }

    @Test
    void differentChatModelMustNotInvalidateThePublishedNodeBinding() {
        var factory = new OpsRuntimeResourceContextFactory(
                mock(OpsRuntimeSkillResolver.class), mock(OpsRuntimeMcpResolver.class));
        var definition = OpsAgentDefinition.builder().modelId("definition-model").build();
        var request = OpsAgentChatRequest.builder().modelId("other-model").build();
        var node = OpsWorkflowNode.builder().nodeId("start").modelId("model-1").build();
        var context = factory.node(definition, node, request, new ArrayList<>(), null);
        var plan = new OpsRuntimeWorkflowBindingAdapter().bind(
                compiled(), runtime(factory.agent(definition, request, new ArrayList<>(), null).getModelId()),
                Map.of("start", runtime(context.getModelId())), contextBundle(),
                "session-1", "run-1", Instant.now());
        assertTrue(plan.nodes().get(0).resources().stream().anyMatch(resource ->
                resource.kind() == BoundWorkflowResourceKind.MODEL && "model-1".equals(resource.resourceId())));
        assertEquals("other-model", request.getModelId());
    }

    @Test
    void unresolvedReferenceAndContextMismatchMustFailClosed() {
        OpsRuntimeWorkflowBindingAdapter adapter = new OpsRuntimeWorkflowBindingAdapter();

        IllegalStateException unresolved = assertThrows(IllegalStateException.class, () -> adapter.bind(
                compiled(), runtime("other-model"), contextBundle(),
                "session-1", "run-1", Instant.now()));
        assertTrue(unresolved.getMessage().contains("RUNTIME_WORKFLOW_RESOURCE_UNRESOLVED:MODEL:model-1"));

        assertThrows(IllegalArgumentException.class, () -> adapter.bind(
                compiled(), runtime("model-1"), contextBundle(),
                "other-session", "run-1", Instant.now()));
    }

    private CompiledAgentDefinitionVersion compiled() {
        CompiledWorkflowNode node = new CompiledWorkflowNode(
                "start",
                AgentWorkflowNodeType.LLM,
                "LLM",
                "llm-node",
                List.of(
                        reference(AgentWorkflowResourceReference.ResourceType.MODEL, "model-1"),
                        reference(AgentWorkflowResourceReference.ResourceType.SKILL, "skill-1"),
                        reference(AgentWorkflowResourceReference.ResourceType.MCP, "mcp-1"),
                        reference(AgentWorkflowResourceReference.ResourceType.KNOWLEDGE_BASE, "kb-1")),
                Map.of("temperature", 0));
        return new CompiledAgentDefinitionVersion(
                1, 7, "definition-hash", "agent-1", "start",
                List.of(node), List.of(), List.of("start"), List.of("start"), List.of("start"),
                List.of("schema-validation", "compiled-plan-assembly"));
    }

    private AgentWorkflowResourceReference reference(
            AgentWorkflowResourceReference.ResourceType type,
            String id) {
        return new AgentWorkflowResourceReference(type, id);
    }

    private OpsRuntimeResourceBundle runtime(String modelId) {
        return OpsRuntimeResourceBundle.builder()
                .agentVersion(7)
                .projectId("project-1")
                .modelId(modelId)
                .knowledgeBaseId("kb-1")
                .skillNames(List.of("skill-1"))
                .mcpIds(List.of("mcp-1"))
                .mcpServers(List.of(OpsMcpServerConfig.builder()
                        .mcpId("mcp-1")
                        .name("mcp-one")
                        .url("https://secret.example")
                        .headers(Map.of("Authorization", "secret"))
                        .build()))
                .metadata(Map.of("modelVersion", "v3"))
                .build();
    }

    private RuntimeContextBundleSnapshot contextBundle() {
        return new RuntimeContextBundleSnapshot(
                1L,
                "bundle-1",
                "bundle-hash",
                "session-1",
                "run-1",
                "project-1",
                "agent-1",
                "actor-1",
                "memory-hash",
                List.of(Map.of("memoryId", "memory-1")),
                List.of(Map.of(
                        "skillId", "skill-1",
                        "version", 4,
                        "skillHash", "skill-hash")),
                "skill-boundary-hash",
                "toolset-boundary-hash",
                "runtime-boundary-hash",
                Map.of(
                        "toolsetRefs", List.of(),
                        "policyRefs", List.of(Map.of("policyId", "policy-1"))),
                Instant.parse("2026-08-02T00:30:00Z"));
    }
}
