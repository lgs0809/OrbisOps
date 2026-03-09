package cn.lgs.orbisops.domain.agentdefinition.service;

import cn.lgs.orbisops.domain.agentdefinition.model.OpsBuiltinSubAgentRole;
import cn.lgs.orbisops.domain.agentdefinition.model.SubAgentToolBoundaryDecision;

import java.util.List;

/** Resolves built-in sub-agent depth, allowlist and permitted runtime tool names. */
public final class SubAgentToolBoundaryPolicy {

    public SubAgentToolBoundaryDecision evaluate(
            String roleValue,
            Integer maxDepth,
            List<String> configuredAllowedTools,
            List<String> availableToolNames) {
        OpsBuiltinSubAgentRole role = OpsBuiltinSubAgentRole.parse(roleValue);
        if (role == null) return SubAgentToolBoundaryDecision.inactive();
        if (maxDepth != null && maxDepth != 1) {
            throw new SecurityException(
                    "SUB_AGENT_DEPTH_EXCEEDED：内置子 Agent 最大深度必须为 1");
        }
        List<String> allowlist = configuredAllowedTools == null
                || configuredAllowedTools.isEmpty()
                ? role.defaultAllowedTools()
                : List.copyOf(configuredAllowedTools);
        if (allowlist.isEmpty()) {
            throw new SecurityException(
                    "SUB_AGENT_TOOL_ALLOWLIST_MISSING：内置子 Agent 缺少工具白名单");
        }
        List<String> permitted = (availableToolNames == null
                ? List.<String>of()
                : availableToolNames).stream()
                .filter(toolName -> allowedTool(toolName, allowlist))
                .toList();
        return new SubAgentToolBoundaryDecision(
                true,
                role.name(),
                1,
                allowlist,
                permitted);
    }

    private boolean allowedTool(String toolName, List<String> allowlist) {
        if (toolName == null || toolName.trim().isBlank()) return false;
        for (String pattern : allowlist) {
            if (pattern == null || pattern.trim().isBlank()) continue;
            String normalized = pattern.trim();
            if (normalized.endsWith("*")
                    && toolName.startsWith(
                    normalized.substring(0, normalized.length() - 1))) {
                return true;
            }
            if (toolName.equals(normalized)) return true;
        }
        return false;
    }
}
