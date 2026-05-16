package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.agentdefinition.compilation.CompiledAgentDefinitionVersion;
import cn.lgs.orbisops.domain.agentdefinition.compilation.NodeDefinitionCompilationStage;
import cn.lgs.orbisops.domain.agentdefinition.compilation.WorkflowCompilationErrorCode;
import cn.lgs.orbisops.domain.agentdefinition.compilation.WorkflowCompilationException;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeType;
import cn.lgs.orbisops.testsupport.OpsAgentDefinitionValidatorTestFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAgentWorkflowStructuralCompilerTest {

    @Test
    void validatorMustExposeCompiledStructuralVersionBeforePublication() {
        OpsAgentDefinitionValidator validator =
                OpsAgentDefinitionValidatorTestFactory.create();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .schemaVersion(1)
                .version(9)
                .definitionHash("hash-9")
                .engine("HYBRID")
                .startNodeId("start")
                .nodes(List.of(
                        node("start", "START", "start"),
                        node("agent", "AGENT", "ops-agent"),
                        node("end", "END", "end")))
                .edges(List.of(
                        OpsGraphEdge.builder().from("start").to("agent").condition("always").build(),
                        OpsGraphEdge.builder().from("agent").to("end").condition("always").build()))
                .build();

        CompiledAgentDefinitionVersion compiled = validator.compile(definition);

        assertEquals(1, compiled.schemaVersion());
        assertEquals(9, compiled.definitionVersion());
        assertEquals("hash-9", compiled.definitionHash());
        assertTrue(compiled.versioned());
        assertEquals(List.of("start", "agent", "end"), compiled.reachableNodeIds());
        assertEquals(List.of("end"), compiled.terminalNodeIds());
        assertEquals(8, compiled.completedStages().size());
        assertFalse(compiled.nodes().stream().anyMatch(node ->
                node.config().containsKey("runtimeClient")));
    }

    @Test
    void disabledTypedNodeMustFailAtCompilerRegistryWithoutFallback() {
        OpsAgentDefinitionValidator validator =
                OpsAgentDefinitionValidatorTestFactory.create();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .schemaVersion(1)
                .engine("CHAT")
                .nodes(List.of(node("parallel", "PARALLEL", "parallel-agent")))
                .build();

        WorkflowCompilationException error = assertThrows(
                WorkflowCompilationException.class,
                () -> validator.compile(definition));

        assertEquals(WorkflowCompilationErrorCode.NODE_COMPILER_NOT_FOUND,
                error.report().failures().get(0).code());
        assertEquals(NodeDefinitionCompilationStage.STAGE_ID,
                error.report().failures().get(0).stageId());
        assertEquals("parallel", error.report().failures().get(0).subjectId());
    }

    @Test
    void simpleChatWithoutExplicitNodesMustCompileToDeterministicImplicitNode() {
        OpsAgentDefinitionValidator validator =
                OpsAgentDefinitionValidatorTestFactory.create();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("simple-chat")
                .engine("CHAT")
                .build();

        CompiledAgentDefinitionVersion compiled = validator.compile(definition);

        assertEquals("__implicit_agent__", compiled.startNodeId());
        assertEquals(1, compiled.nodes().size());
        assertEquals(AgentWorkflowNodeType.LLM, compiled.nodes().get(0).nodeType());
        assertEquals(true, compiled.nodes().get(0).config().get("implicit"));
    }

    @Test
    void futureSchemaMustReturnTypedSchemaFailure() {
        OpsAgentDefinitionValidator validator =
                OpsAgentDefinitionValidatorTestFactory.create();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("future")
                .schemaVersion(2)
                .engine("CHAT")
                .build();

        WorkflowCompilationException error = assertThrows(
                WorkflowCompilationException.class,
                () -> validator.compile(definition));

        assertEquals(WorkflowCompilationErrorCode.SCHEMA_INVALID,
                error.report().failures().get(0).code());
        assertEquals("schema-validation",
                error.report().failures().get(0).stageId());
    }

    @Test
    void registryMustBindOnlyCurrentEnabledSubset() {
        OpsAgentDefinitionValidator validator =
                OpsAgentDefinitionValidatorTestFactory.create();
        var bindings = validator.structuralCompiler().nodeCompilerBindings();

        assertTrue(bindings.keySet().containsAll(List.of(
                AgentWorkflowNodeType.START,
                AgentWorkflowNodeType.END,
                AgentWorkflowNodeType.LLM,
                AgentWorkflowNodeType.AGENT,
                AgentWorkflowNodeType.TOOL,
                AgentWorkflowNodeType.RAG,
                AgentWorkflowNodeType.CONDITION,
                AgentWorkflowNodeType.HUMAN_APPROVAL,
                AgentWorkflowNodeType.SUB_WORKFLOW)));
        assertFalse(bindings.containsKey(AgentWorkflowNodeType.PARALLEL));
        assertFalse(bindings.containsKey(AgentWorkflowNodeType.WAIT));
        assertThrows(UnsupportedOperationException.class,
                () -> bindings.put(AgentWorkflowNodeType.WAIT, "wait"));
    }

    private OpsWorkflowNode node(String id, String type, String agent) {
        return OpsWorkflowNode.builder()
                .nodeId(id)
                .type(type)
                .agent(agent)
                .build();
    }
}
