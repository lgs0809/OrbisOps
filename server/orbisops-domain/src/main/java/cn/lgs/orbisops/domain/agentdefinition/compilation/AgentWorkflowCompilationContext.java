package cn.lgs.orbisops.domain.agentdefinition.compilation;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Mutable, single-compilation context. Results exposed from it are immutable copies. */
public final class AgentWorkflowCompilationContext {

    private final AgentWorkflowDefinition definition;
    private final AgentWorkflowCompilationHooks hooks;
    private final Map<String, CompiledWorkflowNode> compiledNodes = new LinkedHashMap<>();
    private final List<CompiledWorkflowEdge> compiledEdges = new ArrayList<>();
    private final List<String> completedStages = new ArrayList<>();
    private String startNodeId = "";
    private List<String> reachableNodeIds = List.of();
    private List<String> topologicalOrder = List.of();
    private List<String> terminalNodeIds = List.of();
    private CompiledAgentDefinitionVersion output;

    public AgentWorkflowCompilationContext(
            AgentWorkflowDefinition definition,
            AgentWorkflowCompilationHooks hooks) {
        if (definition == null) throw new IllegalArgumentException("WORKFLOW_COMPILATION_DEFINITION_REQUIRED");
        this.definition = definition;
        this.hooks = hooks == null ? AgentWorkflowCompilationHooks.noop() : hooks;
    }

    public AgentWorkflowDefinition definition() {
        return definition;
    }

    public AgentWorkflowCompilationHooks hooks() {
        return hooks;
    }

    public void addCompiledNode(CompiledWorkflowNode node) {
        if (node == null) throw new IllegalArgumentException("COMPILED_WORKFLOW_NODE_REQUIRED");
        if (compiledNodes.putIfAbsent(node.nodeId(), node) != null) {
            throw new IllegalArgumentException("COMPILED_WORKFLOW_NODE_DUPLICATE:" + node.nodeId());
        }
    }

    public void setCompiledEdges(List<CompiledWorkflowEdge> edges) {
        compiledEdges.clear();
        if (edges != null) compiledEdges.addAll(edges);
    }

    public List<CompiledWorkflowNode> compiledNodes() {
        return List.copyOf(compiledNodes.values());
    }

    public List<CompiledWorkflowEdge> compiledEdges() {
        return List.copyOf(compiledEdges);
    }

    public void setControlFlowFacts(
            String startNodeId,
            List<String> reachableNodeIds,
            List<String> topologicalOrder,
            List<String> terminalNodeIds) {
        this.startNodeId = startNodeId == null ? "" : startNodeId.trim();
        this.reachableNodeIds = reachableNodeIds == null ? List.of() : List.copyOf(reachableNodeIds);
        this.topologicalOrder = topologicalOrder == null ? List.of() : List.copyOf(topologicalOrder);
        this.terminalNodeIds = terminalNodeIds == null ? List.of() : List.copyOf(terminalNodeIds);
    }

    public String startNodeId() {
        return startNodeId;
    }

    public List<String> reachableNodeIds() {
        return reachableNodeIds;
    }

    public List<String> topologicalOrder() {
        return topologicalOrder;
    }

    public List<String> terminalNodeIds() {
        return terminalNodeIds;
    }

    public void completeStage(String stageId) {
        String normalized = stageId == null ? "" : stageId.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException("WORKFLOW_COMPILATION_STAGE_REQUIRED");
        completedStages.add(normalized);
    }

    public List<String> completedStages() {
        return List.copyOf(completedStages);
    }

    public void setOutput(CompiledAgentDefinitionVersion output) {
        if (output == null) throw new IllegalArgumentException("COMPILED_AGENT_DEFINITION_REQUIRED");
        this.output = output;
    }

    public CompiledAgentDefinitionVersion output() {
        if (output == null) throw new IllegalStateException("COMPILED_AGENT_DEFINITION_NOT_ASSEMBLED");
        return output;
    }
}
