package cn.lgs.orbisops.domain.agentdefinition.service;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentGraphDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeType;

import java.util.Locale;
import java.util.Set;

/** Domain invariants for a workflow node's type, execution identity and capabilities. */
public final class AgentNodeDefinitionPolicy {

    private static final Set<String> NODE_MODES = Set.of(
            "DIRECT", "LLM", "REACT", "REVIEW", "AUTO", "PLAN");
    private static final Set<String> EXECUTABLE_TYPES = Set.of(
            "CHAT", "LLM", "PLAN", "AGENT", "SUB_AGENT", "ROUTER", "REVIEW",
            "REFLECT", "REPORT", "NOTIFY", "EXECUTE", "AGENTSCOPE", "RAG", "MCP",
            "CUSTOM", "KNOWLEDGE_RETRIEVAL", "TOOL_CALL", "HTTP", "CODE", "TEMPLATE",
            "VARIABLE_MERGE", "SUB_WORKFLOW");

    private final AgentWorkflowNodeTypePolicy workflowTypePolicy;

    public AgentNodeDefinitionPolicy() {
        this(new AgentWorkflowNodeTypePolicy());
    }

    public AgentNodeDefinitionPolicy(AgentWorkflowNodeTypePolicy workflowTypePolicy) {
        if (workflowTypePolicy == null) {
            throw new IllegalArgumentException("WORKFLOW_NODE_TYPE_POLICY_REQUIRED");
        }
        this.workflowTypePolicy = workflowTypePolicy;
    }

    public void validate(AgentGraphDefinition.Node node) {
        if (node == null || !hasText(node.nodeId())) {
            throw new IllegalArgumentException("存在缺少 nodeId 的节点");
        }
        workflowTypePolicy.validateEnabled(node.workflowDefinition());
        String type = AgentWorkflowNodeType.normalizePublishedName(node.type());
        String mode = normalize(node.mode(), "");
        if (node.config()!=null && node.config().containsKey("changeVerification")) {
            if (!"ROUTER".equals(type) || !(node.config().get("changeVerification") instanceof java.util.Map<?,?>)) {
                throw new IllegalArgumentException("CHANGE_VERIFICATION_ROUTER_REQUIRED");
            }
            new cn.lgs.orbisops.domain.runtime.workflow.service.WorkflowChangeVerificationPolicy().validate((java.util.Map<?,?>)node.config().get("changeVerification"));
        }
        if ("SUB_WORKFLOW".equals(type) && node.config() != null && node.config().containsKey("structuredOutputKey")) {
            new DirectActionDataPolicy().validate(java.util.Map.of("structuredOutputKey", node.config().get("structuredOutputKey")));
        }
        if (node.config() != null && node.config().containsKey("observability")) {
            if (!"ROUTER".equals(type) || !(node.config().get("observability") instanceof java.util.Map<?, ?>)) {
                throw new IllegalArgumentException("OBSERVABILITY_ROUTER_CONFIG_REQUIRED");
            }
            new cn.lgs.orbisops.domain.runtime.workflow.service.WorkflowObservabilityPolicy()
                    .validate((java.util.Map<?, ?>) node.config().get("observability"));
        }
        if (hasText(mode) && !NODE_MODES.contains(mode)) {
            throw new IllegalArgumentException(
                    "节点 " + node.nodeId() + " mode 不支持：" + node.mode());
        }
        if ("DIRECT".equals(normalizeAgentMode(node, type))) {
            validateDirectActions(node);
        }
        if (EXECUTABLE_TYPES.contains(type) && !hasText(node.agent())) {
            throw new IllegalArgumentException(
                    "节点 " + node.nodeId() + " 缺少执行 agent");
        }
        if (Boolean.TRUE.equals(node.changePackageEnabled())
                && Set.of("START", "END", "ROUTER").contains(type)) {
            throw new IllegalArgumentException(
                    "节点 " + node.nodeId()
                            + " 是结构节点，不能启用 ChangePackage 工具");
        }
        if (node.config().containsKey("changePackageStatusEnabled")) {
            Object enabled = node.config().get("changePackageStatusEnabled");
            if (!(enabled instanceof Boolean) || (Boolean.TRUE.equals(enabled)
                    && !("AGENT".equals(type) && "REACT".equals(normalizeAgentMode(node, type))))) {
                throw new IllegalArgumentException("CHANGE_PACKAGE_STATUS_REACT_NODE_REQUIRED");
            }
        }
        if (requiresExplicitRuntimeCapability(node, type)
                && lacksExplicitRuntimeCapability(node)) {
            throw new IllegalArgumentException(
                    "节点 " + node.nodeId()
                            + " 是 ReAct/工具执行节点，必须显式选择 MCP、启用 RAG 或配置受支持的只读变更查询能力。"
                            + "不能通过节点名称或路由条件推断工具能力");
        }
    }

    public String normalizedType(AgentGraphDefinition.Node node) {
        String published = AgentWorkflowNodeType.normalizePublishedName(
                node == null ? null : node.type());
        AgentWorkflowNodeType.fromPublishedName(published);
        return published;
    }

    private boolean requiresExplicitRuntimeCapability(
            AgentGraphDefinition.Node node,
            String type) {
        if (Set.of("START", "END", "ROUTER", "REPORT", "NOTIFY").contains(type)) {
            return false;
        }
        if (Set.of("SUB_AGENT", "AGENTSCOPE", "MCP", "TOOL_CALL").contains(type)) {
            return true;
        }
        if (!"AGENT".equals(type)) {
            return false;
        }
        String mode = normalizeAgentMode(node, type);
        String role = normalizeSourceText(configText(node, "role"));
        return "REACT".equals(mode) || "DATA_AGENT".equals(role);
    }

    private boolean lacksExplicitRuntimeCapability(AgentGraphDefinition.Node node) {
        if (Boolean.TRUE.equals(booleanConfig(node, "inheritProjectCapabilities"))) {
            return false;
        }
        if (Boolean.TRUE.equals(node.config().get("changePackageStatusEnabled"))) {
            return false;
        }
        return !node.hasExplicitMcp() && !Boolean.TRUE.equals(node.ragEnabled());
    }

    private void validateDirectActions(AgentGraphDefinition.Node node) {
        Object raw = node == null || node.config() == null ? null : node.config().get("actions");
        if (!(raw instanceof java.util.List<?> actions) || actions.isEmpty()) {
            throw new IllegalArgumentException(
                    "节点 " + node.nodeId() + " DIRECT 模式必须至少配置一个 Action");
        }
        int index = 0;
        for (Object action : actions) {
            index++;
            if (!(action instanceof java.util.Map<?, ?> map)) {
                throw new IllegalArgumentException(
                        "节点 " + node.nodeId() + " DIRECT Action " + index + " 格式无效");
            }
            new DirectActionDataPolicy().validate(map);
            String toolName = mapText(map, "toolName");
            String mcpId = mapText(map, "mcpId");
            String remoteToolName = mapText(map, "remoteToolName");
            boolean mcpAction = hasText(mcpId) || hasText(remoteToolName);
            if (!mcpAction) {
                if (!hasText(toolName)) {
                    throw new IllegalArgumentException(
                            "节点 " + node.nodeId() + " DIRECT Action " + index + " 缺少 toolName");
                }
                continue;
            }
            if (!hasText(mcpId) || !hasText(remoteToolName)) {
                throw new IllegalArgumentException(
                        "节点 " + node.nodeId() + " DIRECT MCP Action " + index
                                + " 必须同时配置 mcpId 与 remoteToolName");
            }
            if (!node.mcpIds().contains(mcpId)) {
                throw new IllegalArgumentException(
                        "节点 " + node.nodeId() + " DIRECT MCP Action " + index
                                + " 引用了未绑定 MCP：" + mcpId);
            }
        }
    }

    private String mapText(java.util.Map<?, ?> source, String key) {
        if (source == null || !source.containsKey(key)) return "";
        Object value = source.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Boolean booleanConfig(AgentGraphDefinition.Node node, String key) {
        if (node == null || node.config() == null) return null;
        Object value = node.config().get(key);
        if (value instanceof Boolean bool) return bool;
        return value == null ? null : Boolean.valueOf(String.valueOf(value));
    }

    private String normalizeAgentMode(AgentGraphDefinition.Node node, String type) {
        String configured = firstText(
                node == null ? null : node.mode(),
                configText(node, "mode"),
                configText(node, "agentMode"));
        String mode = normalize(configured, "");
        if ("REVIEW".equals(mode)) return "LLM";
        if (NODE_MODES.contains(mode)) return mode;
        if ("PLAN".equals(type)) return "PLAN";
        if ("REVIEW".equals(type) || "REFLECT".equals(type)) return "LLM";
        if (Set.of(
                "SUB_AGENT", "EXECUTE", "AGENTSCOPE", "MCP", "TOOL_CALL",
                "KNOWLEDGE_RETRIEVAL").contains(type)) {
            return "REACT";
        }
        return "AUTO";
    }

    private String configText(AgentGraphDefinition.Node node, String key) {
        if (node == null || node.config() == null || !node.config().containsKey(key)) {
            return "";
        }
        Object value = node.config().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String normalizeSourceText(String value) {
        return hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT).replace('-', '_')
                : "";
    }

    private String normalize(String value, String fallback) {
        if (!hasText(value)) return fallback;
        return value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) return value;
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
