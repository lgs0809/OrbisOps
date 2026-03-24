package cn.lgs.orbisops.domain.agentdefinition.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Protocol-neutral structural definition of one executable Agent graph.
 *
 * <p>This model deliberately excludes persistence metadata, JSON annotations,
 * Spring types and runtime adapter objects. It contains only the facts required
 * to enforce Agent graph invariants.</p>
 */
public record AgentGraphDefinition(
        String agentId,
        String engine,
        String startNodeId,
        List<Node> nodes,
        List<Edge> edges,
        List<Loop> loops) {

    public AgentGraphDefinition {
        agentId = text(agentId);
        engine = text(engine);
        startNodeId = text(startNodeId);
        nodes = immutable(nodes);
        edges = immutable(edges);
        loops = immutable(loops);
    }

    public List<AgentWorkflowNodeDefinition> workflowNodes() {
        return nodes.stream()
                .filter(node -> node != null)
                .map(Node::workflowDefinition)
                .toList();
    }

    public List<AgentWorkflowEdgeDefinition> workflowEdges() {
        return edges.stream()
                .filter(edge -> edge != null)
                .map(Edge::workflowDefinition)
                .toList();
    }

    public record Node(
            String nodeId,
            String type,
            String mode,
            String agent,
            String description,
            String instruction,
            String modelId,
            String subEngine,
            String outputKey,
            Boolean ragEnabled,
            String knowledgeBaseId,
            Boolean repairEnabled,
            Boolean changePackageEnabled,
            List<String> skills,
            List<String> mcpIds,
            List<String> executionTargetIds,
            int inlineMcpServerCount,
            Map<String, Object> config) {

        public Node(
                String nodeId,
                String type,
                String mode,
                String agent,
                Boolean ragEnabled,
                Boolean changePackageEnabled,
                List<String> mcpIds,
                int inlineMcpServerCount,
                Map<String, Object> config) {
            this(
                    nodeId, type, mode, agent,
                    "", "", "", "", "",
                    ragEnabled, "", false, changePackageEnabled,
                    List.of(), mcpIds, List.of(), inlineMcpServerCount, config);
        }

        public Node {
            nodeId = text(nodeId);
            type = text(type);
            mode = text(mode);
            agent = text(agent);
            description = text(description);
            instruction = text(instruction);
            modelId = text(modelId);
            subEngine = text(subEngine);
            outputKey = text(outputKey);
            knowledgeBaseId = text(knowledgeBaseId);
            skills = immutable(skills);
            mcpIds = immutable(mcpIds);
            executionTargetIds = immutable(executionTargetIds);
            inlineMcpServerCount = Math.max(0, inlineMcpServerCount);
            config = immutableMap(config);
        }

        public boolean hasExplicitMcp() {
            return !mcpIds.isEmpty() || inlineMcpServerCount > 0;
        }

        public AgentWorkflowNodeDefinition workflowDefinition() {
            List<AgentWorkflowResourceReference> resources = new ArrayList<>();
            addResource(resources, AgentWorkflowResourceReference.ResourceType.MODEL, modelId);
            addResource(resources, AgentWorkflowResourceReference.ResourceType.KNOWLEDGE_BASE,
                    knowledgeBaseId);
            addResources(resources, AgentWorkflowResourceReference.ResourceType.SKILL, skills);
            addResources(resources, AgentWorkflowResourceReference.ResourceType.MCP, mcpIds);
            addResources(resources, AgentWorkflowResourceReference.ResourceType.EXECUTION_TARGET,
                    executionTargetIds);
            for (int index = 0; index < inlineMcpServerCount; index++) {
                addResource(
                        resources,
                        AgentWorkflowResourceReference.ResourceType.INLINE_MCP,
                        nodeId + "#inline-mcp-" + index);
            }
            return new AgentWorkflowNodeDefinition(
                    nodeId,
                    AgentWorkflowNodeType.fromPublishedName(type),
                    type,
                    mode,
                    agent,
                    description,
                    instruction,
                    resources,
                    config);
        }
    }

    public record Edge(
            String edgeId,
            String name,
            String fromNodeId,
            String toNodeId,
            String conditionType,
            String conditionExpression,
            boolean defaultEdge,
            boolean feedback,
            int priority,
            Map<String, Object> dataMapping,
            String description) {

        public Edge(
                String fromNodeId,
                String toNodeId,
                String conditionType,
                boolean feedback) {
            this(
                    "", "", fromNodeId, toNodeId, conditionType, "",
                    "default".equalsIgnoreCase(conditionType),
                    feedback, 0, Map.of(), "");
        }

        public Edge(
                String fromNodeId,
                String toNodeId,
                String conditionType,
                String conditionExpression,
                boolean feedback) {
            this(
                    "", "", fromNodeId, toNodeId, conditionType, conditionExpression,
                    isDefault(conditionType, conditionExpression),
                    feedback, 0, Map.of(), "");
        }

        public Edge {
            edgeId = text(edgeId);
            name = text(name);
            fromNodeId = text(fromNodeId);
            toNodeId = text(toNodeId);
            conditionType = text(conditionType);
            conditionExpression = text(conditionExpression);
            dataMapping = immutableMap(dataMapping);
            description = text(description);
        }

        public AgentWorkflowEdgeDefinition workflowDefinition() {
            AgentWorkflowRouteMode routeMode = AgentWorkflowRouteMode.fromPublishedName(
                    conditionType,
                    defaultEdge);
            return new AgentWorkflowEdgeDefinition(
                    edgeId,
                    name,
                    fromNodeId,
                    toNodeId,
                    routeMode,
                    new AgentWorkflowRuleExpression(routeMode, conditionExpression),
                    defaultEdge,
                    feedback,
                    priority,
                    dataMapping,
                    description);
        }
    }

    public record Loop(
            String loopId,
            List<String> nodeIds,
            List<String> feedbackEdges,
            Integer maxRounds,
            String exitEdge) {

        public Loop {
            loopId = text(loopId);
            nodeIds = immutable(nodeIds);
            feedbackEdges = immutable(feedbackEdges);
            exitEdge = text(exitEdge);
        }
    }

    private static void addResources(
            List<AgentWorkflowResourceReference> resources,
            AgentWorkflowResourceReference.ResourceType type,
            List<String> ids) {
        for (String id : immutable(ids)) addResource(resources, type, id);
    }

    private static void addResource(
            List<AgentWorkflowResourceReference> resources,
            AgentWorkflowResourceReference.ResourceType type,
            String id) {
        if (id == null || id.trim().isBlank()) return;
        resources.add(new AgentWorkflowResourceReference(type, id));
    }

    private static boolean isDefault(String conditionType, String expression) {
        return "default".equalsIgnoreCase(text(conditionType))
                || "default".equalsIgnoreCase(text(expression))
                || "__default__".equalsIgnoreCase(text(expression));
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> values) {
        return values == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
