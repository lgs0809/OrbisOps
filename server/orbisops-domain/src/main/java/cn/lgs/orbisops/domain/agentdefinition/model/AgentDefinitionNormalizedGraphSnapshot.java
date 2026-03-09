package cn.lgs.orbisops.domain.agentdefinition.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable normalized persistence snapshot for one Agent Definition graph. */
public record AgentDefinitionNormalizedGraphSnapshot(
        String agentId,
        List<Node> nodes,
        List<Edge> edges,
        List<AgentScope> agentScopes,
        List<SkillBinding> skillBindings,
        List<McpServerBinding> mcpServerBindings) {

    public AgentDefinitionNormalizedGraphSnapshot {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("AGENT_DEFINITION_GRAPH_AGENT_ID_REQUIRED");
        }
        agentId = agentId.trim();
        nodes = immutable(nodes);
        edges = immutable(edges);
        agentScopes = immutable(agentScopes);
        skillBindings = immutable(skillBindings);
        mcpServerBindings = immutable(mcpServerBindings);
    }

    public enum OwnerType {
        AGENT,
        NODE,
        AGENTSCOPE
    }

    public record Node(String nodeId,
                       String nodeType,
                       String agent,
                       String subEngine,
                       String outputKey,
                       Boolean ragEnabled,
                       String knowledgeBaseId,
                       String description,
                       String instruction,
                       Map<String, Object> config,
                       int sortOrder) {
        public Node {
            nodeId = required(nodeId, "AGENT_DEFINITION_NODE_ID_REQUIRED");
            nodeType = required(nodeType, "AGENT_DEFINITION_NODE_TYPE_REQUIRED");
            config = immutableMap(config);
        }

        public AgentWorkflowNodeDefinition workflowDefinition() {
            String publishedType = AgentWorkflowNodeType.normalizePublishedName(nodeType);
            return new AgentWorkflowNodeDefinition(
                    nodeId,
                    AgentWorkflowNodeType.fromPublishedName(publishedType),
                    publishedType,
                    configText(config, "mode", "agentMode"),
                    agent,
                    description,
                    instruction,
                    List.of(),
                    config);
        }
    }

    public record Edge(String edgeId,
                       String name,
                       String fromNodeId,
                       String toNodeId,
                       String conditionType,
                       String conditionExpression,
                       Boolean defaultEdge,
                       Boolean feedbackEdge,
                       Integer priority,
                       Map<String, Object> dataMapping,
                       String description,
                       int sortOrder) {
        public Edge {
            fromNodeId = required(fromNodeId, "AGENT_DEFINITION_EDGE_FROM_REQUIRED");
            toNodeId = required(toNodeId, "AGENT_DEFINITION_EDGE_TO_REQUIRED");
            conditionType = required(conditionType, "AGENT_DEFINITION_EDGE_CONDITION_TYPE_REQUIRED");
            conditionExpression = required(conditionExpression, "AGENT_DEFINITION_EDGE_CONDITION_REQUIRED");
            dataMapping = immutableMap(dataMapping);
        }

        public AgentWorkflowEdgeDefinition workflowDefinition() {
            boolean isDefault = Boolean.TRUE.equals(defaultEdge)
                    || "default".equalsIgnoreCase(conditionType)
                    || "default".equalsIgnoreCase(conditionExpression)
                    || "__default__".equalsIgnoreCase(conditionExpression);
            AgentWorkflowRouteMode mode = AgentWorkflowRouteMode.fromPublishedName(
                    conditionType,
                    isDefault);
            return new AgentWorkflowEdgeDefinition(
                    edgeId,
                    name,
                    fromNodeId,
                    toNodeId,
                    mode,
                    new AgentWorkflowRuleExpression(mode, conditionExpression),
                    isDefault,
                    Boolean.TRUE.equals(feedbackEdge),
                    priority == null ? 0 : priority,
                    dataMapping,
                    description);
        }
    }

    public record AgentScope(String scopeAgentId,
                             String name,
                             String instruction,
                             String outputKey,
                             Boolean ragEnabled,
                             String knowledgeBaseId,
                             Integer maxIterations,
                             int maxDepth,
                             String role,
                             List<String> allowedToolNames,
                             int sortOrder) {
        public AgentScope {
            scopeAgentId = optional(scopeAgentId);
            allowedToolNames = immutable(allowedToolNames);
        }
    }

    public record SkillBinding(OwnerType ownerType,
                               String ownerId,
                               String skillName) {
        public SkillBinding {
            ownerType = Objects.requireNonNull(ownerType, "AGENT_DEFINITION_SKILL_OWNER_TYPE_REQUIRED");
            ownerId = required(ownerId, "AGENT_DEFINITION_SKILL_OWNER_ID_REQUIRED");
            skillName = required(skillName, "AGENT_DEFINITION_SKILL_NAME_REQUIRED");
        }
    }

    public record McpServerBinding(OwnerType ownerType,
                                   String ownerId,
                                   String serverName,
                                   String description,
                                   String transport,
                                   String command,
                                   String url,
                                   Integer timeoutSeconds,
                                   List<String> args,
                                   Map<String, String> env,
                                   Map<String, String> headers,
                                   Map<String, String> toolCapabilities,
                                   List<String> allowedTools,
                                   List<String> notificationTools,
                                   List<String> blockedTools) {
        public McpServerBinding {
            ownerType = Objects.requireNonNull(ownerType, "AGENT_DEFINITION_MCP_OWNER_TYPE_REQUIRED");
            ownerId = required(ownerId, "AGENT_DEFINITION_MCP_OWNER_ID_REQUIRED");
            serverName = required(serverName, "AGENT_DEFINITION_MCP_SERVER_NAME_REQUIRED");
            transport = required(transport, "AGENT_DEFINITION_MCP_TRANSPORT_REQUIRED");
            args = immutable(args);
            env = immutableMap(env);
            headers = immutableMap(headers);
            toolCapabilities = immutableMap(toolCapabilities);
            allowedTools = immutable(allowedTools);
            notificationTools = immutable(notificationTools);
            blockedTools = immutable(blockedTools);
        }
    }

    private static String required(String value, String error) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(error);
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null ? null : value.trim();
    }

    private static String configText(
            Map<String, Object> config,
            String... keys) {
        if (config == null || keys == null) return "";
        for (String key : keys) {
            Object value = config.get(key);
            if (value != null && !String.valueOf(value).trim().isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return "";
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
