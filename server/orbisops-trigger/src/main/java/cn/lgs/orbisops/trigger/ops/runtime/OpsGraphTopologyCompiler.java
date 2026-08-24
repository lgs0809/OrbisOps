package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncMultiCommandAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;

/** Compiles declarative workflow edges into the Spring AI Graph topology. */
final class OpsGraphTopologyCompiler {

    private final OpsGraphConditionEvaluator conditionEvaluator;
    private final OpsGraphFeedbackLoopPolicy feedbackLoopPolicy;
    private final OpsRuntimeNodeExecutionPolicy nodeExecutionPolicy;
    private final OpsGraphRouteSelector routeSelector;

    OpsGraphTopologyCompiler(OpsGraphConditionEvaluator conditionEvaluator,
                             OpsGraphFeedbackLoopPolicy feedbackLoopPolicy,
                             OpsRuntimeNodeExecutionPolicy nodeExecutionPolicy,
                             OpsGraphRouteSelector routeSelector) {
        this.conditionEvaluator = conditionEvaluator;
        this.feedbackLoopPolicy = feedbackLoopPolicy;
        this.nodeExecutionPolicy = nodeExecutionPolicy;
        this.routeSelector = routeSelector;
    }

    void compile(StateGraph graph,
                 OpsAgentDefinition definition,
                 OpsAgentRunRequestDTO request,
                 OpsAgentChatRequest runtimeRequest,
                 List<OpsWorkflowNode> nodes) throws GraphStateException {
        String startNodeId = StringUtils.hasText(definition.getStartNodeId())
                ? definition.getStartNodeId()
                : nodes.get(0).getNodeId();
        graph.addEdge(START, startNodeId);
        List<OpsGraphEdge> executableEdges = Optional.ofNullable(definition.getEdges())
                .orElse(List.of());
        Map<String, OpsWorkflowNode> nodeById = nodes.stream()
                .collect(Collectors.toMap(
                        OpsWorkflowNode::getNodeId,
                        node -> node,
                        (left, right) -> left,
                        LinkedHashMap::new));
        List<ParallelJoin> parallelJoins = parallelJoins(executableEdges, nodeById);
        Set<String> joinedEdgeKeys = parallelJoins.stream()
                .flatMap(join -> join.sources().stream()
                        .map(source -> feedbackLoopPolicy.graphEdgeKey(source, join.target())))
                .collect(Collectors.toSet());
        String terminalNodeId = nodes.stream()
                .filter(node -> "END".equals(nodeExecutionPolicy.executionNodeType(node)))
                .map(OpsWorkflowNode::getNodeId)
                .findFirst()
                .orElse(null);
        Map<String, List<OpsGraphEdge>> edgeMap = executableEdges.stream()
                .collect(Collectors.groupingBy(
                        OpsGraphEdge::getFrom,
                        LinkedHashMap::new,
                        Collectors.toList()));
        for (Map.Entry<String, List<OpsGraphEdge>> entry : edgeMap.entrySet()) {
            compileOutgoingEdges(
                    graph,
                    definition,
                    request,
                    runtimeRequest,
                    nodeById,
                    joinedEdgeKeys,
                    terminalNodeId,
                    entry);
        }
        for (ParallelJoin join : parallelJoins) {
            graph.addEdge(join.sources(), join.target());
        }
        addImplicitTerminalEdges(graph, nodes, executableEdges);
    }

    private void compileOutgoingEdges(StateGraph graph,
                                      OpsAgentDefinition definition,
                                      OpsAgentRunRequestDTO request,
                                      OpsAgentChatRequest runtimeRequest,
                                      Map<String, OpsWorkflowNode> nodeById,
                                      Set<String> joinedEdgeKeys,
                                      String terminalNodeId,
                                      Map.Entry<String, List<OpsGraphEdge>> entry)
            throws GraphStateException {
        List<OpsGraphEdge> outgoing = entry.getValue();
        List<OpsGraphEdge> conditional = outgoing.stream()
                .filter(conditionEvaluator::isConditionalEdge)
                .toList();
        Optional<OpsGraphEdge> defaultEdge = outgoing.stream()
                .filter(conditionEvaluator::isDefaultEdge)
                .findFirst();
        for (OpsGraphEdge edge : outgoing.stream()
                .filter(conditionEvaluator::isAlwaysEdge)
                .toList()) {
            if (!joinedEdgeKeys.contains(
                    feedbackLoopPolicy.graphEdgeKey(edge.getFrom(), edge.getTo()))) {
                graph.addEdge(edge.getFrom(), edge.getTo());
            }
        }
        if (conditional.isEmpty()) {
            return;
        }
        Map<String, String> mapping = conditionalMapping(
                definition,
                entry.getKey(),
                outgoing,
                conditional,
                defaultEdge,
                terminalNodeId);
        OpsWorkflowNode fromNode = nodeById.get(entry.getKey());
        // A single possible branch has no fan-out. The SDK's parallel-node wrapper
        // returns that branch's first result and can omit its downstream chain.
        if (conditional.size() > 1 && routeSelector.useParallelConditionalEdges(fromNode)) {
            graph.addParallelConditionalEdges(
                    entry.getKey(),
                    AsyncMultiCommandAction.of(state ->
                            CompletableFuture.completedFuture(routeSelector.select(
                                    definition,
                                    request,
                                    state,
                                    conditional,
                                    fromNode,
                                    terminalNodeId,
                                    true,
                                    runtimeRequest))),
                    mapping);
            return;
        }
        graph.addConditionalEdges(
                entry.getKey(),
                AsyncEdgeAction.edge_async(state -> routeSelector.selectSingle(
                        definition,
                        request,
                        state,
                        conditional,
                        fromNode,
                        terminalNodeId,
                        runtimeRequest)),
                mapping);
    }

    private Map<String, String> conditionalMapping(OpsAgentDefinition definition,
                                                   String fromNodeId,
                                                   List<OpsGraphEdge> outgoing,
                                                   List<OpsGraphEdge> conditional,
                                                   Optional<OpsGraphEdge> defaultEdge,
                                                   String terminalNodeId) {
        Map<String, String> mapping = new LinkedHashMap<>();
        conditional.forEach(edge -> mapping.put(edge.getCondition(), edge.getTo()));
        mapping.put(
                "__default__",
                defaultEdge.map(OpsGraphEdge::getTo)
                        .orElseGet(() -> StringUtils.hasText(terminalNodeId)
                                ? terminalNodeId
                                : END));
        if (StringUtils.hasText(terminalNodeId)) {
            mapping.put("__end__", terminalNodeId);
        }
        feedbackLoopPolicy.addExitMappings(
                definition,
                fromNodeId,
                outgoing,
                defaultEdge,
                mapping);
        return mapping;
    }

    private List<ParallelJoin> parallelJoins(List<OpsGraphEdge> edges,
                                             Map<String, OpsWorkflowNode> nodeById) {
        Map<String, List<OpsGraphEdge>> outgoingByNode = edges.stream()
                .collect(Collectors.groupingBy(
                        OpsGraphEdge::getFrom,
                        LinkedHashMap::new,
                        Collectors.toList()));
        Map<String, ParallelJoin> joins = new LinkedHashMap<>();
        for (Map.Entry<String, List<OpsGraphEdge>> entry : outgoingByNode.entrySet()) {
            List<OpsGraphEdge> conditional = entry.getValue().stream()
                    .filter(conditionEvaluator::isConditionalEdge)
                    .toList();
            OpsWorkflowNode router = nodeById.get(entry.getKey());
            if (conditional.size() < 2
                    || !routeSelector.useParallelConditionalEdges(router)) {
                continue;
            }
            List<String> branchSources = conditional.stream()
                    .map(OpsGraphEdge::getTo)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
            if (branchSources.size() < 2) {
                continue;
            }
            Set<String> commonTargets = commonAlwaysTargets(
                    branchSources, outgoingByNode);
            for (String target : commonTargets) {
                String joinKey = String.join(",", branchSources) + "->" + target;
                joins.putIfAbsent(joinKey, new ParallelJoin(branchSources, target));
            }
        }
        return List.copyOf(joins.values());
    }

    private Set<String> commonAlwaysTargets(
            List<String> branchSources,
            Map<String, List<OpsGraphEdge>> outgoingByNode) {
        Set<String> commonTargets = null;
        for (String branchSource : branchSources) {
            Set<String> targets = outgoingByNode
                    .getOrDefault(branchSource, List.of()).stream()
                    .filter(conditionEvaluator::isAlwaysEdge)
                    .map(OpsGraphEdge::getTo)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (commonTargets == null) {
                commonTargets = new LinkedHashSet<>(targets);
            } else {
                commonTargets.retainAll(targets);
            }
        }
        return commonTargets == null ? Set.of() : commonTargets;
    }

    private void addImplicitTerminalEdges(StateGraph graph,
                                          List<OpsWorkflowNode> nodes,
                                          List<OpsGraphEdge> executableEdges)
            throws GraphStateException {
        Set<String> nodesWithOutEdges = executableEdges.stream()
                .map(OpsGraphEdge::getFrom)
                .collect(Collectors.toSet());
        for (OpsWorkflowNode node : nodes) {
            if (!nodesWithOutEdges.contains(node.getNodeId())) {
                graph.addEdge(node.getNodeId(), END);
            }
        }
    }

    private record ParallelJoin(List<String> sources, String target) {
    }
}
