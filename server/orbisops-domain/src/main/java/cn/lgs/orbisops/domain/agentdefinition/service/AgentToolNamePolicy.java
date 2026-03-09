package cn.lgs.orbisops.domain.agentdefinition.service;

import java.util.List;

/** Validates model-visible tool names used by MCP and sub-agent allowlists. */
public final class AgentToolNamePolicy {

    public void validateAll(List<String> toolNames, String owner) {
        for (String toolName : toolNames == null ? List.<String>of() : toolNames) {
            validate(toolName, owner);
        }
    }

    public void validate(String toolName, String owner) {
        if (toolName == null || toolName.trim().isBlank() || "*".equals(toolName)) {
            return;
        }
        if (!toolName.matches("[A-Za-z0-9_.:-]+\\*?")) {
            throw new IllegalArgumentException(owner + " 包含非法工具名：" + toolName);
        }
    }
}
