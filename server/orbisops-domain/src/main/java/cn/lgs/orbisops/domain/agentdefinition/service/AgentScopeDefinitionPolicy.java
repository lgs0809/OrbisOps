package cn.lgs.orbisops.domain.agentdefinition.service;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentScopeDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.OpsBuiltinSubAgentRole;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Structural and security invariants for AgentScope sub-agent definitions. */
public final class AgentScopeDefinitionPolicy {

    private final AgentToolNamePolicy toolNamePolicy;

    public AgentScopeDefinitionPolicy(AgentToolNamePolicy toolNamePolicy) {
        if (toolNamePolicy == null) {
            throw new IllegalArgumentException("AGENT_TOOL_NAME_POLICY_REQUIRED");
        }
        this.toolNamePolicy = toolNamePolicy;
    }

    public void validate(AgentScopeDefinition definition) {
        List<AgentScopeDefinition.Scope> scopes = definition == null
                ? List.of()
                : definition.scopes();
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < scopes.size(); index++) {
            AgentScopeDefinition.Scope scope = scopes.get(index);
            String id = firstText(
                    scope == null ? null : scope.agentId(),
                    scope == null ? null : scope.name(),
                    "agentscope-" + index);
            if (!ids.add(id)) {
                throw new IllegalArgumentException("ReAct 子 Agent ID 重复：" + id);
            }
            if (scope == null || !hasText(scope.instruction())) {
                throw new IllegalArgumentException("ReAct 子 Agent 缺少 instruction：" + id);
            }
            if (scope.maxDepth() != null && scope.maxDepth() != 1) {
                throw new IllegalArgumentException(
                        "ReAct 子 Agent 最大深度必须为 1：" + id);
            }
            OpsBuiltinSubAgentRole role = OpsBuiltinSubAgentRole.parse(scope.role());
            if (role == null) continue;
            List<String> allowedTools = scope.allowedToolNames().isEmpty()
                    ? role.defaultAllowedTools()
                    : scope.allowedToolNames();
            if (allowedTools.isEmpty()) {
                throw new IllegalArgumentException(
                        "内置子 Agent 必须配置非空工具白名单：" + id);
            }
            toolNamePolicy.validateAll(
                    allowedTools,
                    "AgentScope " + id + " allowedToolNames");
        }
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
