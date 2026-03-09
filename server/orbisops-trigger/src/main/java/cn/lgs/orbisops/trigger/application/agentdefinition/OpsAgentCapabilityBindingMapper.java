package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityBinding;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityBindingSnapshot;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityOwnerType;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityType;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionLifecycle;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentScopeConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpServerConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkflowNode;
import com.alibaba.fastjson.JSON;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Maps Trigger compatibility definitions and views to typed capability binding models. */
public final class OpsAgentCapabilityBindingMapper {

    public AgentCapabilityBindingSnapshot map(OpsAgentDefinition definition) {
        return map(definition, true);
    }

    public AgentCapabilityBindingSnapshot mapForValidation(OpsAgentDefinition definition) {
        return map(definition, false);
    }

    private AgentCapabilityBindingSnapshot map(OpsAgentDefinition definition, boolean requireAgentId) {
        if (definition == null) {
            throw new IllegalArgumentException("AGENT_CAPABILITY_DEFINITION_REQUIRED");
        }
        if (requireAgentId && !StringUtils.hasText(definition.getAgentId())) {
            throw new IllegalArgumentException("AGENT_CAPABILITY_DEFINITION_REQUIRED");
        }
        String agentId = StringUtils.hasText(definition.getAgentId())
                ? definition.getAgentId().trim()
                : "__capability_validation__";
        int version = definition.getVersion() == null ? 0 : definition.getVersion();
        AgentDefinitionLifecycle lifecycle = AgentDefinitionLifecycle.require(
                textOr(definition.getLifecycle(), "DRAFT"));
        String projectId = textOr(definition.getProjectId(), "");
        List<AgentCapabilityBinding> bindings = new ArrayList<>();

        appendOwnerBindings(bindings, agentId, version, lifecycle, projectId,
                AgentCapabilityOwnerType.AGENT, "",
                definition.getSkills(), definition.getMcpIds(), definition.getExecutionTargetIds(),
                definition.getKnowledgeBaseId(), definition.getMcpServers());

        for (OpsWorkflowNode node : list(definition.getNodes())) {
            if (node == null || !StringUtils.hasText(node.getNodeId())) {
                continue;
            }
            appendOwnerBindings(bindings, agentId, version, lifecycle, projectId,
                    AgentCapabilityOwnerType.NODE, node.getNodeId(),
                    node.getSkills(), node.getMcpIds(), node.getExecutionTargetIds(),
                    node.getKnowledgeBaseId(), node.getMcpServers());
        }

        List<OpsAgentScopeConfig> scopes = list(definition.getAgentscopeAgents());
        for (int index = 0; index < scopes.size(); index++) {
            OpsAgentScopeConfig scope = scopes.get(index);
            if (scope == null) {
                continue;
            }
            String ownerId = textOr(scope.getAgentId(), "agentscope_" + index);
            appendOwnerBindings(bindings, agentId, version, lifecycle, projectId,
                    AgentCapabilityOwnerType.AGENTSCOPE, ownerId,
                    scope.getSkills(), scope.getMcpIds(), scope.getExecutionTargetIds(),
                    scope.getKnowledgeBaseId(), scope.getMcpServers());
        }

        return new AgentCapabilityBindingSnapshot(agentId, version, lifecycle, projectId, bindings);
    }

    public List<Map<String, Object>> views(List<AgentCapabilityBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return List.of();
        }
        return bindings.stream().map(this::view).toList();
    }

    public List<Map<String, Object>> compatibilityViews(List<AgentCapabilityBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return List.of();
        }
        return bindings.stream().map(binding -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("agentId", binding.agentId());
            row.put("projectId", binding.projectId());
            row.put("ownerType", binding.ownerType().name());
            row.put("nodeId", binding.nodeId());
            row.put("capabilityType", binding.capabilityType().storageValue());
            row.put("capabilityId", binding.capabilityId());
            row.put("capabilityScope", binding.capabilityScope().name());
            row.put("bindConfig", binding.bindConfig());
            return row;
        }).toList();
    }

    private void appendOwnerBindings(List<AgentCapabilityBinding> bindings,
                                     String agentId,
                                     int version,
                                     AgentDefinitionLifecycle lifecycle,
                                     String projectId,
                                     AgentCapabilityOwnerType ownerType,
                                     String ownerId,
                                     List<String> skills,
                                     List<String> projectTools,
                                     List<String> executionTargets,
                                     String knowledgeBaseId,
                                     List<OpsMcpServerConfig> inlineMcpServers) {
        appendReferences(bindings, agentId, version, lifecycle, projectId,
                ownerType, ownerId, AgentCapabilityType.SKILL, skills);
        appendReferences(bindings, agentId, version, lifecycle, projectId,
                ownerType, ownerId, AgentCapabilityType.PROJECT_TOOL, projectTools);
        appendReferences(bindings, agentId, version, lifecycle, projectId,
                ownerType, ownerId, AgentCapabilityType.EXECUTION_TARGET, executionTargets);
        appendReference(bindings, agentId, version, lifecycle, projectId,
                ownerType, ownerId, AgentCapabilityType.KNOWLEDGE_BASE, knowledgeBaseId, Map.of());
        for (OpsMcpServerConfig server : list(inlineMcpServers)) {
            if (server == null || !StringUtils.hasText(server.getName())) {
                continue;
            }
            Map<String, Object> bindConfig = new LinkedHashMap<>();
            bindConfig.put("transport", textOr(server.getTransport(), "stdio"));
            bindConfig.put("allowedTools", list(server.getAllowedTools()));
            bindConfig.put("toolCapabilities", map(server.getToolCapabilities()));
            appendReference(bindings, agentId, version, lifecycle, projectId,
                    ownerType, ownerId, AgentCapabilityType.INLINE_MCP_SERVER,
                    server.getName(), bindConfig);
        }
    }

    private void appendReferences(List<AgentCapabilityBinding> bindings,
                                  String agentId,
                                  int version,
                                  AgentDefinitionLifecycle lifecycle,
                                  String projectId,
                                  AgentCapabilityOwnerType ownerType,
                                  String ownerId,
                                  AgentCapabilityType capabilityType,
                                  List<String> capabilityIds) {
        for (String capabilityId : list(capabilityIds)) {
            appendReference(bindings, agentId, version, lifecycle, projectId,
                    ownerType, ownerId, capabilityType, capabilityId, Map.of());
        }
    }

    private void appendReference(List<AgentCapabilityBinding> bindings,
                                 String agentId,
                                 int version,
                                 AgentDefinitionLifecycle lifecycle,
                                 String projectId,
                                 AgentCapabilityOwnerType ownerType,
                                 String ownerId,
                                 AgentCapabilityType capabilityType,
                                 String capabilityId,
                                 Map<String, Object> bindConfig) {
        if (!StringUtils.hasText(capabilityId)) {
            return;
        }
        bindings.add(AgentCapabilityBinding.create(
                agentId,
                version,
                lifecycle,
                projectId,
                ownerType,
                textOr(ownerId, ""),
                capabilityType,
                capabilityId,
                bindConfig));
    }

    private Map<String, Object> view(AgentCapabilityBinding binding) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", binding.id());
        row.put("agentId", binding.agentId());
        row.put("version", binding.version());
        row.put("lifecycle", binding.lifecycle().name());
        row.put("projectId", binding.projectId());
        row.put("ownerType", binding.ownerType().name());
        row.put("nodeId", binding.nodeId());
        row.put("capabilityType", binding.capabilityType().storageValue());
        row.put("capabilityId", binding.capabilityId());
        row.put("capabilityScope", binding.capabilityScope().name());
        row.put("bindConfigJson", JSON.toJSONString(binding.bindConfig()));
        row.put("createBy", binding.createBy());
        row.put("createTime", binding.createTime() == null ? null : Timestamp.from(binding.createTime()));
        return row;
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
