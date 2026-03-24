package cn.lgs.orbisops.domain.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.compilation.AgentNodeCompiler;
import cn.lgs.orbisops.domain.agentdefinition.compilation.AgentWorkflowCompilationContext;
import cn.lgs.orbisops.domain.agentdefinition.compilation.AgentWorkflowCompilationHooks;
import cn.lgs.orbisops.domain.agentdefinition.compilation.AgentWorkflowNodeCompiler;
import cn.lgs.orbisops.domain.agentdefinition.compilation.AgentWorkflowNodeCompilerRegistry;
import cn.lgs.orbisops.domain.agentdefinition.compilation.LlmNodeCompiler;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentGraphDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentWorkflowNodeCompilerRegistryTest {

    @Test
    void registryMustExpandSupportedTypesAndExposeImmutableDescription() {
        AgentWorkflowNodeCompilerRegistry registry = new AgentWorkflowNodeCompilerRegistry(
                List.of(new LlmNodeCompiler(), new AgentNodeCompiler()));

        assertEquals("llm-node", registry.describe().get(AgentWorkflowNodeType.LLM));
        assertEquals("agent-node", registry.describe().get(AgentWorkflowNodeType.AGENT));
        assertThrows(UnsupportedOperationException.class,
                () -> registry.describe().put(AgentWorkflowNodeType.TOOL, "tool"));
    }

    @Test
    void duplicateCompilerIdAndTypeMustFailClosed() {
        AgentWorkflowNodeCompiler first = compiler("same", AgentWorkflowNodeType.LLM);
        AgentWorkflowNodeCompiler duplicateId = compiler("same", AgentWorkflowNodeType.AGENT);
        assertThrows(IllegalArgumentException.class,
                () -> new AgentWorkflowNodeCompilerRegistry(List.of(first, duplicateId)));

        AgentWorkflowNodeCompiler duplicateType = compiler("other", AgentWorkflowNodeType.LLM);
        assertThrows(IllegalArgumentException.class,
                () -> new AgentWorkflowNodeCompilerRegistry(List.of(first, duplicateType)));
    }

    @Test
    void unsupportedKnownTypeMustNeverFallBackToAnotherCompiler() {
        AgentWorkflowNodeCompilerRegistry registry = new AgentWorkflowNodeCompilerRegistry(
                List.of(new LlmNodeCompiler()));
        AgentWorkflowNodeDefinition node = new AgentWorkflowNodeDefinition(
                "parallel", AgentWorkflowNodeType.PARALLEL, "PARALLEL",
                "", "agent", "", "", List.of(), Map.of());

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> registry.compile(node, context()));

        assertEquals("WORKFLOW_NODE_COMPILER_NOT_FOUND:PARALLEL", error.getMessage());
    }

    private AgentWorkflowNodeCompiler compiler(String id, AgentWorkflowNodeType type) {
        return new AgentWorkflowNodeCompiler() {
            @Override
            public String compilerId() {
                return id;
            }

            @Override
            public Set<AgentWorkflowNodeType> supportedTypes() {
                return Set.of(type);
            }

            @Override
            public cn.lgs.orbisops.domain.agentdefinition.compilation.CompiledWorkflowNode compile(
                    AgentWorkflowNodeDefinition definition,
                    AgentWorkflowCompilationContext context) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private AgentWorkflowCompilationContext context() {
        AgentGraphDefinition graph = new AgentGraphDefinition(
                "agent", "CHAT", "",
                List.of(new AgentGraphDefinition.Node(
                        "node", "CHAT", "", "agent", false, false,
                        List.of(), 0, Map.of())),
                List.of(), List.of());
        return new AgentWorkflowCompilationContext(
                new AgentWorkflowDefinition(1, 0, "", graph),
                AgentWorkflowCompilationHooks.noop());
    }
}
