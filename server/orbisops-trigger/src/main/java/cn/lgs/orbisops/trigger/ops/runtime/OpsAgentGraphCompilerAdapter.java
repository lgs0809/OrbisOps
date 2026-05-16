package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeDefinition;
import cn.lgs.orbisops.domain.agentdefinition.service.AgentWorkflowNodeTypePolicy;
import cn.lgs.orbisops.domain.worksession.graph.AgentGraphCompiler;
import cn.lgs.orbisops.domain.worksession.graph.AgentGraphModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class OpsAgentGraphCompilerAdapter {

    private static final String SYNTHETIC_END_NODE_ID = "__runtime_end__";

    private final AgentGraphCompiler compiler = new AgentGraphCompiler();
    private final OpsAgentGraphDefinitionMapper definitionMapper =
            new OpsAgentGraphDefinitionMapper();
    private final AgentWorkflowNodeTypePolicy nodeTypePolicy =
            new AgentWorkflowNodeTypePolicy();

    public AgentGraphModel.Compiled compile(OpsAgentDefinition definition) {
        return compile(definition, safe(definition == null ? null : definition.getNodes()));
    }

    public AgentGraphModel.Compiled compile(OpsAgentDefinition definition,
                                            List<OpsWorkflowNode> runtimeNodes) {
        if (definition == null) throw new IllegalArgumentException("OPS_AGENT_DEFINITION_REQUIRED");
        List<OpsWorkflowNode> effectiveNodes = safe(runtimeNodes);
        List<AgentGraphModel.Node> nodes = effectiveNodes.stream()
                .map(node -> new AgentGraphModel.Node(node.getNodeId(), nodeType(node)))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        Set<String> loopEdgeIds = new LinkedHashSet<>();
        for (OpsLoopPolicy loop : safe(definition.getLoops())) {
            if (loop.getFeedbackEdges() != null) loopEdgeIds.addAll(loop.getFeedbackEdges());
        }

        List<OpsGraphEdge> explicitEdges = safe(definition.getEdges());
        List<AgentGraphModel.Edge> edges = explicitEdges.stream()
                .map(edge -> new AgentGraphModel.Edge(
                        edge.getFrom(), edge.getTo(), route(edge),
                        edge.getPriority() == null ? 0 : edge.getPriority(),
                        Boolean.TRUE.equals(edge.getFeedback()) || loopEdgeIds.contains(edge.getEdgeId())))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        normalizeRuntimeExitEdges(effectiveNodes, explicitEdges, nodes, edges);

        Set<AgentGraphModel.EdgeKey> allowedLoops = edges.stream()
                .filter(AgentGraphModel.Edge::loopEdge)
                .map(AgentGraphModel.Edge::key)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        String startNodeId = StringUtils.hasText(definition.getStartNodeId())
                ? definition.getStartNodeId()
                : effectiveNodes.stream().findFirst().map(OpsWorkflowNode::getNodeId).orElse("");
        return compiler.compile(new AgentGraphModel.Definition(
                StringUtils.hasText(definition.getAgentId()) ? definition.getAgentId() : "anonymous-agent",
                startNodeId, nodes, edges, allowedLoops));
    }

    private void normalizeRuntimeExitEdges(List<OpsWorkflowNode> runtimeNodes,
                                           List<OpsGraphEdge> explicitEdges,
                                           List<AgentGraphModel.Node> domainNodes,
                                           List<AgentGraphModel.Edge> domainEdges) {
        Map<String, List<OpsGraphEdge>> outgoing = explicitEdges.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        OpsGraphEdge::getFrom, LinkedHashMap::new, java.util.stream.Collectors.toList()));
        String explicitEndNodeId = runtimeNodes.stream()
                .filter(node -> "END".equals(nodeType(node)))
                .map(OpsWorkflowNode::getNodeId)
                .findFirst()
                .orElse("");
        boolean syntheticEndRequired = false;
        String syntheticEndNodeId = uniqueSyntheticEndNodeId(domainNodes);
        Set<String> identities = new LinkedHashSet<>();
        domainEdges.forEach(edge -> identities.add(edgeIdentity(edge.sourceNodeId(), edge.targetNodeId(), edge.route())));

        for (Map.Entry<String, List<OpsGraphEdge>> entry : outgoing.entrySet()) {
            List<OpsGraphEdge> sourceEdges = entry.getValue();
            boolean hasConditional = sourceEdges.stream().anyMatch(this::isConditionalEdge);
            if (!hasConditional) continue;

            boolean hasDefault = sourceEdges.stream().anyMatch(this::isDefaultEdge);
            String exitNodeId = explicitEndNodeId;
            if (!StringUtils.hasText(exitNodeId) && !hasDefault) {
                exitNodeId = syntheticEndNodeId;
                syntheticEndRequired = true;
            }
            if (!hasDefault && StringUtils.hasText(exitNodeId)) {
                addSyntheticEdge(domainEdges, identities, entry.getKey(), exitNodeId, "__default__");
            }
            if (StringUtils.hasText(explicitEndNodeId)) {
                addSyntheticEdge(domainEdges, identities, entry.getKey(), explicitEndNodeId, "__end__");
            }
        }
        if (syntheticEndRequired) {
            domainNodes.add(new AgentGraphModel.Node(syntheticEndNodeId, "END"));
        }
    }

    private String uniqueSyntheticEndNodeId(List<AgentGraphModel.Node> nodes) {
        Set<String> nodeIds = nodes.stream().map(AgentGraphModel.Node::nodeId).collect(java.util.stream.Collectors.toSet());
        String candidate = SYNTHETIC_END_NODE_ID;
        int suffix = 1;
        while (nodeIds.contains(candidate)) {
            candidate = SYNTHETIC_END_NODE_ID + suffix++;
        }
        return candidate;
    }

    private void addSyntheticEdge(List<AgentGraphModel.Edge> edges,
                                  Set<String> identities,
                                  String source,
                                  String target,
                                  String route) {
        String identity = edgeIdentity(source, target, route);
        if (identities.add(identity)) {
            edges.add(new AgentGraphModel.Edge(source, target, route, Integer.MIN_VALUE, false));
        }
    }

    private String edgeIdentity(String source, String target, String route) {
        return source + "\u0000" + target + "\u0000" + route;
    }

    private String nodeType(OpsWorkflowNode node) {
        AgentWorkflowNodeDefinition typed = definitionMapper.workflowNode(node);
        nodeTypePolicy.validateEnabled(typed);
        return typed.publishedType();
    }

    private String route(OpsGraphEdge edge) {
        definitionMapper.workflowEdge(edge);
        if (isDefaultEdge(edge)) return "__default__";
        if (StringUtils.hasText(edge.getCondition())) return edge.getCondition().trim();
        if (StringUtils.hasText(edge.getName())) return edge.getName().trim();
        return "__always__";
    }

    private boolean isConditionalEdge(OpsGraphEdge edge) {
        String type = normalizeConditionType(edge == null ? null : edge.getConditionType());
        if (edge == null || isDefaultEdge(edge)) return false;
        if ("always".equals(type)) return isConditionalCondition(edge.getCondition());
        return isConditionalCondition(edge.getCondition())
                || Set.of("route_match", "review_decision", "expression", "contains", "error").contains(type);
    }

    private boolean isDefaultEdge(OpsGraphEdge edge) {
        if (edge == null) return false;
        return Boolean.TRUE.equals(edge.getDefaultEdge())
                || "default".equals(normalizeConditionType(edge.getConditionType()))
                || isDefaultCondition(edge.getCondition());
    }

    private boolean isConditionalCondition(String condition) {
        return StringUtils.hasText(condition) && !isAlwaysCondition(condition) && !isDefaultCondition(condition);
    }

    private boolean isAlwaysCondition(String condition) {
        return !StringUtils.hasText(condition) || "always".equalsIgnoreCase(condition.trim());
    }

    private boolean isDefaultCondition(String condition) {
        return StringUtils.hasText(condition)
                && ("__default__".equalsIgnoreCase(condition.trim())
                || "default".equalsIgnoreCase(condition.trim()));
    }

    private String normalizeConditionType(String conditionType) {
        if (!StringUtils.hasText(conditionType)) return "always";
        return conditionType.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private <T> List<T> safe(List<T> value) {
        return value == null ? List.of() : value;
    }
}
