package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionResourceValidationRequest;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentGraphDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentMcpServerDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentScopeDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowEdgeDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeType;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowResourceReference;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowRouteMode;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowRuleExpression;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Maps mutable inbound/runtime Agent DTOs into the pure Agent Definition domain model. */
final class OpsAgentGraphDefinitionMapper {

    AgentGraphDefinition map(OpsAgentDefinition definition) {
        if (definition == null) return null;
        return new AgentGraphDefinition(
                definition.getAgentId(),
                definition.getEngine(),
                definition.getStartNodeId(),
                nodes(definition.getNodes()),
                edges(definition.getEdges()),
                loops(definition.getLoops()));
    }

    private AgentGraphDefinition compilationGraph(OpsAgentDefinition definition) {
        AgentGraphDefinition graph = map(definition);
        if (graph == null || !graph.nodes().isEmpty()) return graph;
        String engine = definition.getEngine() == null
                ? "CHAT"
                : definition.getEngine().trim().toUpperCase(Locale.ROOT);
        if (engine.contains("GRAPH") || engine.contains("HYBRID")) return graph;
        String nodeId = "__implicit_agent__";
        String publishedType = engine.contains("AGENTSCOPE") ? "AGENT" : "CHAT";
        AgentGraphDefinition.Node implicit = new AgentGraphDefinition.Node(
                nodeId,
                publishedType,
                "AUTO",
                firstText(definition.getAgentId(), "implicit-agent"),
                "Implicit node for non-graph Agent compilation",
                "",
                "",
                "",
                "",
                false,
                "",
                false,
                false,
                List.of(),
                List.of(),
                List.of(),
                0,
                Map.of("implicit", true));
        return new AgentGraphDefinition(
                graph.agentId(), graph.engine(), nodeId,
                List.of(implicit), List.of(), List.of());
    }

    AgentWorkflowDefinition workflowDefinition(OpsAgentDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("OPS_AGENT_DEFINITION_REQUIRED");
        }
        return new AgentWorkflowDefinition(
                definition.getSchemaVersion() == null
                        ? AgentWorkflowDefinition.CURRENT_SCHEMA_VERSION
                        : definition.getSchemaVersion(),
                definition.getVersion() == null ? 0 : definition.getVersion(),
                definition.getDefinitionHash(),
                compilationGraph(definition));
    }

    AgentDefinitionResourceValidationRequest resources(OpsAgentDefinition definition) {
        if (definition == null) {
            return new AgentDefinitionResourceValidationRequest("", List.of());
        }
        List<AgentDefinitionResourceValidationRequest.ResourceOwner> owners = new ArrayList<>();
        owners.add(resourceOwner(
                "Agent",
                definition.getSkills(),
                definition.getMcpIds(),
                definition.getModelId(),
                definition.getKnowledgeBaseId(),
                definition.getMcpServers()));
        for (OpsWorkflowNode node : definition.getNodes() == null
                ? List.<OpsWorkflowNode>of()
                : definition.getNodes()) {
            if (node == null) continue;
            owners.add(resourceOwner(
                    "节点 " + text(node.getNodeId()),
                    node.getSkills(),
                    node.getMcpIds(),
                    node.getModelId(),
                    node.getKnowledgeBaseId(),
                    node.getMcpServers()));
        }
        List<OpsAgentScopeConfig> scopes = definition.getAgentscopeAgents() == null
                ? List.of()
                : definition.getAgentscopeAgents();
        for (int index = 0; index < scopes.size(); index++) {
            OpsAgentScopeConfig scope = scopes.get(index);
            if (scope == null) continue;
            String id = firstText(
                    scope.getAgentId(),
                    scope.getName(),
                    "agentscope-" + index);
            owners.add(resourceOwner(
                    "AgentScope " + id,
                    scope.getSkills(),
                    scope.getMcpIds(),
                    scope.getModelId(),
                    scope.getKnowledgeBaseId(),
                    scope.getMcpServers()));
        }
        return new AgentDefinitionResourceValidationRequest(
                definition.getProjectId(),
                owners);
    }

    AgentGraphDefinition.Node node(OpsWorkflowNode node) {
        if (node == null) return null;
        return new AgentGraphDefinition.Node(
                node.getNodeId(),
                node.getType(),
                node.getMode(),
                node.getAgent(),
                node.getDescription(),
                node.getInstruction(),
                node.getModelId(),
                node.getSubEngine(),
                node.getOutputKey(),
                node.getRagEnabled(),
                node.getKnowledgeBaseId(),
                node.getRepairEnabled(),
                node.getChangePackageEnabled(),
                node.getSkills(),
                node.getMcpIds(),
                node.getExecutionTargetIds(),
                node.getMcpServers() == null ? 0 : node.getMcpServers().size(),
                node.getConfig() == null ? Map.of() : node.getConfig());
    }

    AgentWorkflowNodeDefinition workflowNode(OpsWorkflowNode node) {
        if (node == null) return null;
        List<AgentWorkflowResourceReference> resources = new ArrayList<>();
        addResource(resources, AgentWorkflowResourceReference.ResourceType.MODEL, node.getModelId());
        addResource(resources, AgentWorkflowResourceReference.ResourceType.KNOWLEDGE_BASE,
                node.getKnowledgeBaseId());
        addResources(resources, AgentWorkflowResourceReference.ResourceType.SKILL, node.getSkills());
        addResources(resources, AgentWorkflowResourceReference.ResourceType.MCP, node.getMcpIds());
        addResources(resources, AgentWorkflowResourceReference.ResourceType.EXECUTION_TARGET,
                node.getExecutionTargetIds());
        List<OpsMcpServerConfig> inlineServers = node.getMcpServers() == null
                ? List.of()
                : node.getMcpServers();
        for (int index = 0; index < inlineServers.size(); index++) {
            OpsMcpServerConfig server = inlineServers.get(index);
            String id = firstText(
                    server == null ? null : server.getName(),
                    node.getNodeId() + "#inline-mcp-" + index);
            addResource(resources, AgentWorkflowResourceReference.ResourceType.INLINE_MCP, id);
        }
        String publishedType = AgentWorkflowNodeType.normalizePublishedName(node.getType());
        return new AgentWorkflowNodeDefinition(
                node.getNodeId(),
                AgentWorkflowNodeType.fromPublishedName(publishedType),
                publishedType,
                node.getMode(),
                node.getAgent(),
                node.getDescription(),
                node.getInstruction(),
                resources,
                node.getConfig() == null ? Map.of() : node.getConfig());
    }

    AgentScopeDefinition scopes(OpsAgentDefinition definition) {
        List<OpsAgentScopeConfig> scopes = definition == null
                ? List.of()
                : definition.getAgentscopeAgents();
        return new AgentScopeDefinition(scopes == null
                ? List.of()
                : scopes.stream().map(this::scope).toList());
    }

    AgentMcpServerDefinition mcpServers(List<OpsMcpServerConfig> servers) {
        return new AgentMcpServerDefinition(servers == null
                ? List.of()
                : servers.stream().map(this::mcpServer).toList());
    }

    private AgentScopeDefinition.Scope scope(OpsAgentScopeConfig scope) {
        if (scope == null) return null;
        return new AgentScopeDefinition.Scope(
                scope.getAgentId(),
                scope.getName(),
                scope.getInstruction(),
                scope.getMaxDepth(),
                scope.getRole(),
                scope.getAllowedToolNames());
    }

    private AgentMcpServerDefinition.Server mcpServer(OpsMcpServerConfig server) {
        if (server == null) return null;
        return new AgentMcpServerDefinition.Server(
                server.getName(),
                server.getTransport(),
                server.getCommand(),
                server.getUrl(),
                server.getAllowedTools(),
                server.getNotificationTools(),
                server.getBlockedTools(),
                server.getToolCapabilities());
    }

    private AgentDefinitionResourceValidationRequest.ResourceOwner resourceOwner(
            String owner,
            List<String> skills,
            List<String> mcpIds,
            String modelId,
            String knowledgeBaseId,
            List<OpsMcpServerConfig> inlineMcpServers) {
        return new AgentDefinitionResourceValidationRequest.ResourceOwner(
                owner,
                skills,
                mcpIds,
                modelId,
                knowledgeBaseId,
                mcpServers(inlineMcpServers));
    }

    private void addResources(
            List<AgentWorkflowResourceReference> resources,
            AgentWorkflowResourceReference.ResourceType type,
            List<String> ids) {
        if (ids == null) return;
        for (String id : ids) addResource(resources, type, id);
    }

    private void addResource(
            List<AgentWorkflowResourceReference> resources,
            AgentWorkflowResourceReference.ResourceType type,
            String id) {
        if (id == null || id.trim().isBlank()) return;
        resources.add(new AgentWorkflowResourceReference(type, id));
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isBlank()) return value.trim();
        }
        return "";
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private List<AgentGraphDefinition.Node> nodes(List<OpsWorkflowNode> nodes) {
        return nodes == null ? List.of() : nodes.stream().map(this::node).toList();
    }

    private List<AgentGraphDefinition.Edge> edges(List<OpsGraphEdge> edges) {
        return edges == null ? List.of() : edges.stream().map(this::edge).toList();
    }

    private AgentGraphDefinition.Edge edge(OpsGraphEdge edge) {
        if (edge == null) return null;
        boolean defaultEdge = Boolean.TRUE.equals(edge.getDefaultEdge())
                || "default".equalsIgnoreCase(text(edge.getConditionType()))
                || "default".equalsIgnoreCase(text(edge.getCondition()))
                || "__default__".equalsIgnoreCase(text(edge.getCondition()));
        return new AgentGraphDefinition.Edge(
                edge.getEdgeId(),
                edge.getName(),
                edge.getFrom(),
                edge.getTo(),
                edge.getConditionType(),
                edge.getCondition(),
                defaultEdge,
                Boolean.TRUE.equals(edge.getFeedback()),
                edge.getPriority() == null ? 0 : edge.getPriority(),
                edge.getDataMapping() == null ? Map.of() : edge.getDataMapping(),
                edge.getDescription());
    }

    AgentWorkflowEdgeDefinition workflowEdge(OpsGraphEdge edge) {
        if (edge == null) return null;
        boolean defaultEdge = Boolean.TRUE.equals(edge.getDefaultEdge())
                || "default".equalsIgnoreCase(text(edge.getConditionType()))
                || "default".equalsIgnoreCase(text(edge.getCondition()))
                || "__default__".equalsIgnoreCase(text(edge.getCondition()));
        AgentWorkflowRouteMode routeMode = AgentWorkflowRouteMode.fromPublishedName(
                edge.getConditionType(),
                defaultEdge);
        return new AgentWorkflowEdgeDefinition(
                edge.getEdgeId(),
                edge.getName(),
                edge.getFrom(),
                edge.getTo(),
                routeMode,
                new AgentWorkflowRuleExpression(routeMode, edge.getCondition()),
                defaultEdge,
                Boolean.TRUE.equals(edge.getFeedback()),
                edge.getPriority() == null ? 0 : edge.getPriority(),
                edge.getDataMapping() == null ? Map.of() : edge.getDataMapping(),
                edge.getDescription());
    }

    private List<AgentGraphDefinition.Loop> loops(List<OpsLoopPolicy> loops) {
        return loops == null ? List.of() : loops.stream().map(this::loop).toList();
    }

    private AgentGraphDefinition.Loop loop(OpsLoopPolicy loop) {
        if (loop == null) return null;
        return new AgentGraphDefinition.Loop(
                loop.getLoopId(),
                loop.getNodes(),
                loop.getFeedbackEdges(),
                loop.getMaxRounds(),
                loop.getExitEdge());
    }
}
