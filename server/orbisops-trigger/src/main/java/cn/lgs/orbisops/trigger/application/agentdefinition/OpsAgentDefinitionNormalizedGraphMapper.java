package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionNormalizedGraphSnapshot;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionNormalizedGraphSnapshot.AgentScope;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionNormalizedGraphSnapshot.Edge;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionNormalizedGraphSnapshot.McpServerBinding;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionNormalizedGraphSnapshot.Node;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionNormalizedGraphSnapshot.OwnerType;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionNormalizedGraphSnapshot.SkillBinding;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentScopeConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsGraphEdge;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpServerConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkflowNode;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Maps the Trigger compatibility model into the normalized Domain persistence snapshot. */
public final class OpsAgentDefinitionNormalizedGraphMapper {

    public AgentDefinitionNormalizedGraphSnapshot map(OpsAgentDefinition definition) {
        if (definition == null || !StringUtils.hasText(definition.getAgentId())) {
            throw new IllegalArgumentException("AGENT_DEFINITION_GRAPH_REQUIRED");
        }
        String agentId = definition.getAgentId().trim();
        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        List<AgentScope> agentScopes = new ArrayList<>();
        List<SkillBinding> skillBindings = new ArrayList<>();
        List<McpServerBinding> mcpServerBindings = new ArrayList<>();

        appendBindings(OwnerType.AGENT, agentId, definition.getSkills(), definition.getMcpServers(),
                skillBindings, mcpServerBindings);

        List<OpsWorkflowNode> workflowNodes = list(definition.getNodes());
        for (int index = 0; index < workflowNodes.size(); index++) {
            OpsWorkflowNode node = workflowNodes.get(index);
            if (node == null) {
                continue;
            }
            nodes.add(new Node(
                    node.getNodeId(),
                    textOr(node.getType(), "CHAT"),
                    node.getAgent(),
                    node.getSubEngine(),
                    node.getOutputKey(),
                    node.getRagEnabled(),
                    node.getKnowledgeBaseId(),
                    node.getDescription(),
                    node.getInstruction(),
                    map(node.getConfig()),
                    index));
            appendBindings(OwnerType.NODE, node.getNodeId(), node.getSkills(), node.getMcpServers(),
                    skillBindings, mcpServerBindings);
        }

        List<OpsGraphEdge> graphEdges = list(definition.getEdges());
        for (int index = 0; index < graphEdges.size(); index++) {
            OpsGraphEdge edge = graphEdges.get(index);
            if (edge == null) {
                continue;
            }
            edges.add(new Edge(
                    edge.getEdgeId(),
                    edge.getName(),
                    edge.getFrom(),
                    edge.getTo(),
                    textOr(edge.getConditionType(), "always"),
                    textOr(edge.getCondition(), "always"),
                    edge.getDefaultEdge(),
                    edge.getFeedback(),
                    edge.getPriority(),
                    map(edge.getDataMapping()),
                    edge.getDescription(),
                    index));
        }

        List<OpsAgentScopeConfig> scopes = list(definition.getAgentscopeAgents());
        for (int index = 0; index < scopes.size(); index++) {
            OpsAgentScopeConfig scope = scopes.get(index);
            if (scope == null) {
                continue;
            }
            String ownerId = textOr(scope.getAgentId(), "agentscope_" + index);
            agentScopes.add(new AgentScope(
                    scope.getAgentId(),
                    scope.getName(),
                    scope.getInstruction(),
                    scope.getOutputKey(),
                    scope.getRagEnabled(),
                    scope.getKnowledgeBaseId(),
                    scope.getMaxIterations(),
                    scope.getMaxDepth() == null ? 1 : scope.getMaxDepth(),
                    scope.getRole(),
                    list(scope.getAllowedToolNames()),
                    index));
            appendBindings(OwnerType.AGENTSCOPE, ownerId, scope.getSkills(), scope.getMcpServers(),
                    skillBindings, mcpServerBindings);
        }

        return new AgentDefinitionNormalizedGraphSnapshot(
                agentId, nodes, edges, agentScopes, skillBindings, mcpServerBindings);
    }

    private void appendBindings(OwnerType ownerType,
                                String ownerId,
                                List<String> skills,
                                List<OpsMcpServerConfig> servers,
                                List<SkillBinding> skillBindings,
                                List<McpServerBinding> mcpServerBindings) {
        for (String skill : list(skills)) {
            if (StringUtils.hasText(skill)) {
                skillBindings.add(new SkillBinding(ownerType, ownerId, skill));
            }
        }
        for (OpsMcpServerConfig server : list(servers)) {
            if (server == null || !StringUtils.hasText(server.getName())) {
                continue;
            }
            mcpServerBindings.add(new McpServerBinding(
                    ownerType,
                    ownerId,
                    server.getName(),
                    server.getDescription(),
                    textOr(server.getTransport(), "stdio"),
                    server.getCommand(),
                    server.getUrl(),
                    server.getTimeoutSeconds(),
                    list(server.getArgs()),
                    map(server.getEnv()),
                    map(server.getHeaders()),
                    map(server.getToolCapabilities()),
                    list(server.getAllowedTools()),
                    list(server.getNotificationTools()),
                    list(server.getBlockedTools())));
        }
    }

    private String textOr(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private <T> List<T> list(List<T> values) {
        return values == null ? List.of() : values;
    }

    private <K, V> Map<K, V> map(Map<K, V> values) {
        return values == null ? Map.of() : values;
    }
}
