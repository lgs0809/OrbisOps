package cn.lgs.orbisops.domain.agentdefinition.service;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionLifecycle;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionVersionState;

/**
 * Domain policy governing which immutable Agent Definition version may be
 * observed or executed for a project.
 */
public final class AgentDefinitionResolutionPolicy {

    public void assertVisible(AgentDefinitionVersionState state,
                              boolean explicitVersion,
                              boolean previewDraft) {
        requireState(state);
        String suffix = explicitVersion ? "@" + state.version() : "";
        if (state.lifecycle() == AgentDefinitionLifecycle.DISABLED) {
            throw new IllegalArgumentException("Agent 定义已停用：" + state.agentId() + suffix);
        }
        if (explicitVersion
                && !previewDraft
                && (state.lifecycle() == AgentDefinitionLifecycle.DRAFT
                || state.lifecycle() == AgentDefinitionLifecycle.VALIDATED)) {
            throw new IllegalArgumentException("Agent 版本尚未发布：" + state.agentId() + "@" + state.version());
        }
    }

    public void assertRunnableInProject(AgentDefinitionVersionState state, String projectId) {
        requireState(state);
        String normalizedProjectId = required(projectId, "AGENT_PROJECT_ID_REQUIRED");
        if (state.projectId().isBlank()) {
            throw new IllegalArgumentException("Agent " + state.agentId()
                    + " 是平台蓝图，不能直接运行；请先复制到项目 " + normalizedProjectId);
        }
        if (!normalizedProjectId.equals(state.projectId())) {
            throw new IllegalArgumentException("Agent " + state.agentId() + " 属于项目 "
                    + state.projectId() + "，不能在项目 " + normalizedProjectId + " 中运行");
        }
    }

    public boolean belongsToProject(AgentDefinitionVersionState state, String projectId) {
        if (state == null || projectId == null) {
            return false;
        }
        return projectId.trim().equals(state.projectId());
    }

    private void requireState(AgentDefinitionVersionState state) {
        if (state == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_STATE_REQUIRED");
        }
    }

    private String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(error);
        }
        return normalized;
    }
}
