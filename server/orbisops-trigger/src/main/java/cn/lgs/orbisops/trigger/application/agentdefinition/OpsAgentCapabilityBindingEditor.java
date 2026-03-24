package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityOwnerType;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityType;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentScopeConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkflowNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Applies legacy HTTP binding requests to the Trigger compatibility definition. */
@Component
public final class OpsAgentCapabilityBindingEditor {

    public List<Map<String, Object>> requestBindings(Map<String, Object> request) {
        return listOfMaps(request == null ? null : request.get("bindings"));
    }

    public void applyBindings(OpsAgentDefinition definition, List<Map<String, Object>> bindings) {
        if (definition == null) {
            throw new IllegalArgumentException("Agent 绑定更新目标不能为空");
        }
        List<BindingRequest> requests = validateRequests(definition, bindings);
        clearBindings(definition);
        for (BindingRequest request : requests) {
            switch (request.ownerType()) {
                case AGENT -> applyBindingToAgent(definition, request.capabilityType(), request.capabilityId());
                case NODE -> Optional.ofNullable(definition.getNodes()).orElse(List.of()).stream()
                        .filter(node -> request.ownerId().equals(node.getNodeId()))
                        .findFirst()
                        .ifPresent(node -> applyBindingToNode(node, request.capabilityType(), request.capabilityId()));
                case AGENTSCOPE -> Optional.ofNullable(definition.getAgentscopeAgents()).orElse(List.of()).stream()
                        .filter(agent -> request.ownerId().equals(agent.getAgentId())
                                || request.ownerId().equals(agent.getName()))
                        .findFirst()
                        .ifPresent(agent -> applyBindingToAgentScope(agent, request.capabilityType(), request.capabilityId()));
            }
        }
    }

    private List<BindingRequest> validateRequests(OpsAgentDefinition definition,
                                                  List<Map<String, Object>> bindings) {
        Set<String> nodeIds = Optional.ofNullable(definition.getNodes()).orElse(List.of()).stream()
                .map(OpsWorkflowNode::getNodeId)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> agentScopeIds = Optional.ofNullable(definition.getAgentscopeAgents()).orElse(List.of()).stream()
                .flatMap(agent -> java.util.stream.Stream.of(agent.getAgentId(), agent.getName()))
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        List<BindingRequest> requests = new ArrayList<>();
        for (Map<String, Object> binding : Optional.ofNullable(bindings).orElse(List.of())) {
            String ownerValue = text(binding.get("ownerType"), "AGENT");
            String ownerId = text(binding.get("nodeId"), "");
            String capabilityValue = text(binding.get("capabilityType"), "");
            String capabilityId = text(binding.get("capabilityId"), "");
            AgentCapabilityOwnerType ownerType;
            AgentCapabilityType capabilityType;
            try {
                ownerType = AgentCapabilityOwnerType.require(ownerValue);
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("不支持的绑定对象类型：" + ownerValue, error);
            }
            try {
                capabilityType = AgentCapabilityType.require(capabilityValue);
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("不支持的能力类型：" + capabilityValue, error);
            }
            if (capabilityType == AgentCapabilityType.INLINE_MCP_SERVER) {
                throw new IllegalArgumentException("不允许通过绑定接口写入内联 MCP 配置");
            }
            if (!StringUtils.hasText(capabilityId)) {
                throw new IllegalArgumentException("能力绑定缺少 capabilityId");
            }
            if (ownerType == AgentCapabilityOwnerType.NODE && !nodeIds.contains(ownerId)) {
                throw new IllegalArgumentException("能力绑定引用了不存在的节点：" + ownerId);
            }
            if (ownerType == AgentCapabilityOwnerType.AGENTSCOPE && !agentScopeIds.contains(ownerId)) {
                throw new IllegalArgumentException("能力绑定引用了不存在的子 Agent：" + ownerId);
            }
            requests.add(new BindingRequest(ownerType, ownerId, capabilityType, capabilityId));
        }
        return requests;
    }

    private void clearBindings(OpsAgentDefinition definition) {
        definition.setSkills(new ArrayList<>());
        definition.setMcpIds(new ArrayList<>());
        definition.setExecutionTargetIds(new ArrayList<>());
        definition.setKnowledgeBaseId("");
        for (OpsWorkflowNode node : Optional.ofNullable(definition.getNodes()).orElse(List.of())) {
            node.setSkills(new ArrayList<>());
            node.setMcpIds(new ArrayList<>());
            node.setExecutionTargetIds(new ArrayList<>());
            node.setKnowledgeBaseId("");
        }
        for (OpsAgentScopeConfig agent : Optional.ofNullable(definition.getAgentscopeAgents()).orElse(List.of())) {
            agent.setSkills(new ArrayList<>());
            agent.setMcpIds(new ArrayList<>());
            agent.setExecutionTargetIds(new ArrayList<>());
            agent.setKnowledgeBaseId("");
        }
    }

    private void applyBindingToAgent(OpsAgentDefinition definition,
                                     AgentCapabilityType capabilityType,
                                     String capabilityId) {
        switch (capabilityType) {
            case SKILL -> definition.setSkills(uniqueAppend(definition.getSkills(), capabilityId));
            case PROJECT_TOOL -> definition.setMcpIds(uniqueAppend(definition.getMcpIds(), capabilityId));
            case EXECUTION_TARGET -> definition.setExecutionTargetIds(
                    uniqueAppend(definition.getExecutionTargetIds(), capabilityId));
            case KNOWLEDGE_BASE -> definition.setKnowledgeBaseId(capabilityId);
            case INLINE_MCP_SERVER -> throw new IllegalArgumentException("不允许写入内联 MCP 配置");
        }
    }

    private void applyBindingToAgentScope(OpsAgentScopeConfig agent,
                                          AgentCapabilityType capabilityType,
                                          String capabilityId) {
        switch (capabilityType) {
            case SKILL -> agent.setSkills(uniqueAppend(agent.getSkills(), capabilityId));
            case PROJECT_TOOL -> agent.setMcpIds(uniqueAppend(agent.getMcpIds(), capabilityId));
            case EXECUTION_TARGET -> agent.setExecutionTargetIds(
                    uniqueAppend(agent.getExecutionTargetIds(), capabilityId));
            case KNOWLEDGE_BASE -> agent.setKnowledgeBaseId(capabilityId);
            case INLINE_MCP_SERVER -> throw new IllegalArgumentException("不允许写入内联 MCP 配置");
        }
    }

    private void applyBindingToNode(OpsWorkflowNode node,
                                    AgentCapabilityType capabilityType,
                                    String capabilityId) {
        switch (capabilityType) {
            case SKILL -> node.setSkills(uniqueAppend(node.getSkills(), capabilityId));
            case PROJECT_TOOL -> node.setMcpIds(uniqueAppend(node.getMcpIds(), capabilityId));
            case EXECUTION_TARGET -> node.setExecutionTargetIds(
                    uniqueAppend(node.getExecutionTargetIds(), capabilityId));
            case KNOWLEDGE_BASE -> node.setKnowledgeBaseId(capabilityId);
            case INLINE_MCP_SERVER -> throw new IllegalArgumentException("不允许写入内联 MCP 配置");
        }
    }

    private List<String> uniqueAppend(List<String> values, String value) {
        LinkedHashSet<String> set = new LinkedHashSet<>(Optional.ofNullable(values).orElse(List.of()));
        set.add(value.trim());
        return new ArrayList<>(set);
    }

    private List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> rawMap) {
                Map<String, Object> map = new LinkedHashMap<>();
                rawMap.forEach((key, rawValue) -> map.put(String.valueOf(key), rawValue));
                result.add(map);
            }
        }
        return result;
    }

    private String text(Object value, String defaultValue) {
        return value == null || !StringUtils.hasText(String.valueOf(value))
                ? defaultValue
                : String.valueOf(value).trim();
    }

    private record BindingRequest(AgentCapabilityOwnerType ownerType,
                                  String ownerId,
                                  AgentCapabilityType capabilityType,
                                  String capabilityId) {
    }
}
