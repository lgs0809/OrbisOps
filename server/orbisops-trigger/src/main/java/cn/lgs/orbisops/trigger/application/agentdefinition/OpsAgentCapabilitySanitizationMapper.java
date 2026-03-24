package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.application.agentdefinition.AgentCapabilitySanitizationRequest;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityOwnerDecision;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityOwnerSelection;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentScopeConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkflowNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Maps mutable Agent DTO capability owners to and from typed sanitization models. */
final class OpsAgentCapabilitySanitizationMapper {

    private static final String ROOT_KEY = "AGENT";

    AgentCapabilitySanitizationRequest request(
            OpsAgentDefinition definition,
            String projectId) {
        if (definition == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_REQUIRED");
        }
        List<AgentCapabilityOwnerSelection> selections = new ArrayList<>();
        selections.add(selection(
                ROOT_KEY,
                definition.getSkills(),
                definition.getMcpIds(),
                definition.getExecutionTargetIds(),
                false));
        List<OpsWorkflowNode> nodes = definition.getNodes() == null
                ? List.of()
                : definition.getNodes();
        for (int index = 0; index < nodes.size(); index++) {
            OpsWorkflowNode node = nodes.get(index);
            if (node == null) continue;
            selections.add(selection(
                    nodeKey(node, index),
                    node.getSkills(),
                    node.getMcpIds(),
                    node.getExecutionTargetIds(),
                    isReactNode(node)));
        }
        List<OpsAgentScopeConfig> scopes = definition.getAgentscopeAgents() == null
                ? List.of()
                : definition.getAgentscopeAgents();
        for (int index = 0; index < scopes.size(); index++) {
            OpsAgentScopeConfig scope = scopes.get(index);
            if (scope == null) continue;
            selections.add(selection(
                    scopeKey(scope, index),
                    scope.getSkills(),
                    scope.getMcpIds(),
                    scope.getExecutionTargetIds(),
                    false));
        }
        return new AgentCapabilitySanitizationRequest(projectId, selections);
    }

    void apply(
            OpsAgentDefinition definition,
            List<AgentCapabilityOwnerDecision> decisions) {
        if (definition == null) return;
        Map<String, AgentCapabilityOwnerDecision> byOwner = new LinkedHashMap<>();
        for (AgentCapabilityOwnerDecision decision : decisions == null
                ? List.<AgentCapabilityOwnerDecision>of()
                : decisions) {
            if (decision != null) byOwner.put(decision.ownerKey(), decision);
        }
        applyRoot(definition, byOwner.get(ROOT_KEY));
        List<OpsWorkflowNode> nodes = definition.getNodes() == null
                ? List.of()
                : definition.getNodes();
        for (int index = 0; index < nodes.size(); index++) {
            OpsWorkflowNode node = nodes.get(index);
            if (node == null) continue;
            applyNode(node, byOwner.get(nodeKey(node, index)));
        }
        List<OpsAgentScopeConfig> scopes = definition.getAgentscopeAgents() == null
                ? List.of()
                : definition.getAgentscopeAgents();
        for (int index = 0; index < scopes.size(); index++) {
            OpsAgentScopeConfig scope = scopes.get(index);
            if (scope == null) continue;
            applyScope(scope, byOwner.get(scopeKey(scope, index)));
        }
    }

    private AgentCapabilityOwnerSelection selection(
            String ownerKey,
            List<String> skills,
            List<String> projectTools,
            List<String> executionTargets,
            boolean enableRagWhenNoProjectTool) {
        return new AgentCapabilityOwnerSelection(
                ownerKey,
                skills,
                projectTools,
                executionTargets,
                enableRagWhenNoProjectTool);
    }

    private void applyRoot(
            OpsAgentDefinition definition,
            AgentCapabilityOwnerDecision decision) {
        if (decision == null) return;
        definition.setKnowledgeBaseId(decision.knowledgeBaseId());
        definition.setSkills(decision.skillIds());
        definition.setMcpIds(decision.projectToolIds());
        definition.setExecutionTargetIds(decision.executionTargetIds());
    }

    private void applyNode(
            OpsWorkflowNode node,
            AgentCapabilityOwnerDecision decision) {
        if (decision == null) return;
        node.setKnowledgeBaseId(decision.knowledgeBaseId());
        node.setSkills(decision.skillIds());
        node.setMcpIds(decision.projectToolIds());
        node.setExecutionTargetIds(decision.executionTargetIds());
        if (decision.forceRagEnabled()) node.setRagEnabled(true);
    }

    private void applyScope(
            OpsAgentScopeConfig scope,
            AgentCapabilityOwnerDecision decision) {
        if (decision == null) return;
        scope.setKnowledgeBaseId(decision.knowledgeBaseId());
        scope.setSkills(decision.skillIds());
        scope.setMcpIds(decision.projectToolIds());
        scope.setExecutionTargetIds(decision.executionTargetIds());
    }

    private String nodeKey(OpsWorkflowNode node, int index) {
        return "NODE:" + firstText(node == null ? null : node.getNodeId(), "node-" + index);
    }

    private String scopeKey(OpsAgentScopeConfig scope, int index) {
        return "AGENTSCOPE:" + firstText(
                scope == null ? null : scope.getAgentId(),
                scope == null ? null : scope.getName(),
                "agentscope-" + index);
    }

    private boolean isReactNode(OpsWorkflowNode node) {
        String type = normalize(node == null ? null : node.getType());
        String mode = normalize(node == null ? null : node.getMode());
        String subEngine = normalize(node == null ? null : node.getSubEngine());
        return "AGENTSCOPE".equals(type)
                || "AGENTSCOPE".equals(subEngine)
                || "REACT".equals(mode);
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isBlank()) return value.trim();
        }
        return "";
    }
}
