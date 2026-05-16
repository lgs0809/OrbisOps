package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.agentdefinition.compilation.AgentWorkflowControlFlowCompiler;
import cn.lgs.orbisops.domain.agentdefinition.compilation.AgentWorkflowControlFlowFacts;
import cn.lgs.orbisops.domain.agentdefinition.compilation.CompiledWorkflowEdge;
import cn.lgs.orbisops.domain.agentdefinition.compilation.CompiledWorkflowNode;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowDefinition;
import cn.lgs.orbisops.domain.worksession.graph.AgentGraphCompiler;
import cn.lgs.orbisops.domain.worksession.graph.AgentGraphModel;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** ACL that reuses the Work Session graph compiler without coupling bounded contexts. */
public final class OpsAgentWorkflowControlFlowCompilerAdapter
        implements AgentWorkflowControlFlowCompiler {

    private final AgentGraphCompiler compiler;

    public OpsAgentWorkflowControlFlowCompilerAdapter() {
        this(new AgentGraphCompiler());
    }

    OpsAgentWorkflowControlFlowCompilerAdapter(AgentGraphCompiler compiler) {
        if (compiler == null) throw new IllegalArgumentException("AGENT_GRAPH_COMPILER_REQUIRED");
        this.compiler = compiler;
    }

    @Override
    public AgentWorkflowControlFlowFacts compile(
            AgentWorkflowDefinition definition,
            List<CompiledWorkflowNode> compiledNodes,
            List<CompiledWorkflowEdge> compiledEdges) {
        if (definition == null) throw new IllegalArgumentException("WORKFLOW_DEFINITION_REQUIRED");
        List<CompiledWorkflowNode> nodesSource = compiledNodes == null ? List.of() : compiledNodes;
        List<CompiledWorkflowEdge> edgesSource = compiledEdges == null ? List.of() : compiledEdges;
        List<AgentGraphModel.Node> nodes = nodesSource.stream()
                .map(node -> new AgentGraphModel.Node(node.nodeId(), node.publishedType()))
                .toList();
        if (nodes.isEmpty()) throw new IllegalArgumentException("WORKFLOW_COMPILED_NODES_REQUIRED");
        Set<String> declaredLoopEdgeIds = definition.graph().loops().stream()
                .flatMap(loop -> loop.feedbackEdges().stream())
                .filter(edgeId -> edgeId != null && !edgeId.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<AgentGraphModel.Edge> edges = edgesSource.stream()
                .map(edge -> new AgentGraphModel.Edge(
                        edge.sourceNodeId(),
                        edge.targetNodeId(),
                        route(edge),
                        edge.priority(),
                        edge.feedbackEdge() || declaredLoopEdgeIds.contains(edge.edgeId())))
                .toList();
        Set<AgentGraphModel.EdgeKey> allowedLoops = edges.stream()
                .filter(AgentGraphModel.Edge::loopEdge)
                .map(AgentGraphModel.Edge::key)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        String startNodeId = definition.graph().startNodeId().isBlank()
                ? nodes.get(0).nodeId()
                : definition.graph().startNodeId();
        AgentGraphModel.Compiled compiled = compiler.compile(new AgentGraphModel.Definition(
                definition.graph().agentId(),
                startNodeId,
                nodes,
                edges,
                allowedLoops));
        return new AgentWorkflowControlFlowFacts(
                compiled.startNodeId(),
                compiled.reachableNodeIds(),
                compiled.executionOrder(),
                compiled.terminalNodeIds());
    }

    private String route(CompiledWorkflowEdge edge) {
        if (edge.defaultRoute()) return "__default__";
        if (!edge.rule().expression().isBlank()) return edge.rule().expression();
        return edge.routeMode().name();
    }
}
