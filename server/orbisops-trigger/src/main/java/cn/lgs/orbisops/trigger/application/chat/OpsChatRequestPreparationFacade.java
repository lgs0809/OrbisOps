package cn.lgs.orbisops.trigger.application.chat;

import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionKind;
import cn.lgs.orbisops.trigger.application.chatsession.OpsChatSessionApplicationFacade;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.UUID;

/** Server-owned Chat preparation: identity, session, project and explicit/default Agent binding only. */
public final class OpsChatRequestPreparationFacade {

    private static final String REACT_MODE = "AGENT";
    private static final String WORKFLOW_MODE = "WORKFLOW";

    private final ProjectDefinitionApplicationService projects;
    private final OpsChatSessionApplicationFacade sessions;

    public OpsChatRequestPreparationFacade(
            ProjectDefinitionApplicationService projects,
            OpsChatSessionApplicationFacade sessions) {
        if (projects == null || sessions == null) {
            throw new IllegalArgumentException("CHAT_REQUEST_PREPARATION_DEPENDENCIES_REQUIRED");
        }
        this.projects = projects;
        this.sessions = sessions;
    }

    public OpsPreparedChatRequest prepareOrCreate(
            OpsAgentChatRequest request,
            String scopedUserId,
            boolean enforceSessionWrite) {
        return prepare(
                request == null ? new OpsAgentChatRequest() : request,
                scopedUserId,
                enforceSessionWrite);
    }

    public OpsPreparedChatRequest prepareRequired(
            OpsAgentChatRequest request,
            String scopedUserId,
            boolean enforceSessionWrite) {
        if (request == null) throw new IllegalArgumentException("聊天请求不能为空");
        return prepare(request, scopedUserId, enforceSessionWrite);
    }

    public void applyProjectAgent(OpsAgentChatRequest request) {
        if (request == null) return;
        String projectId = requireProjectId(request.getProjectId());
        boolean explicit = StringUtils.hasText(request.getAgentDefinitionId())
                || request.getAgentDefinition() != null;
        if (!explicit) {
            request.setAgentDefinitionId(requireDefaultAgent(projectId));
        }
        request.setMode(explicit ? WORKFLOW_MODE : REACT_MODE);
        if (request.getMetadata() == null) {
            request.setMetadata(new LinkedHashMap<>());
        }
        request.getMetadata().put("projectId", projectId);
        request.getMetadata().put("defaultProjectAgent", !explicit);
        request.getMetadata().put("executionStyle", explicit ? "WORKFLOW" : "REACT");
        request.getMetadata().put(
                "assistantRoute",
                explicit ? "USER_SELECTED_WORKFLOW" : "DEFAULT_REACT");
        request.getMetadata().put(
                "workflowSelectionSource",
                explicit ? "USER_SELECTED_DRAG_DROP" : "DEFAULT_PROJECT_AGENT");
        request.getMetadata().put(
                "workflowSelectionReason",
                explicit ? "FIXED_GRAPH_SELECTED" : "DEFAULT_REACT_RUNTIME");
    }

    /**
     * Session binding may replace the provisional project-default Agent with the
     * server-resolved session Agent. Recompute only the execution style from that
     * trusted definition instead of running the whole preparation pipeline again.
     */
    public void applyBoundAgentExecutionStyle(OpsAgentChatRequest request) {
        if (request == null || request.getAgentDefinition() == null) return;
        boolean workflow = AgentDefinitionKind.parse(
                request.getAgentDefinition().getDefinitionKind()) == AgentDefinitionKind.SPECIALIZED_WORKFLOW;
        request.setMode(workflow ? WORKFLOW_MODE : REACT_MODE);
        if (request.getMetadata() == null) request.setMetadata(new LinkedHashMap<>());
        request.getMetadata().put("executionStyle", workflow ? "WORKFLOW" : "REACT");
        request.getMetadata().put(
                "assistantRoute",
                workflow ? "SESSION_BOUND_WORKFLOW" : "SESSION_BOUND_REACT");
    }

    private OpsPreparedChatRequest prepare(
            OpsAgentChatRequest request,
            String scopedUserId,
            boolean enforceSessionWrite) {
        bindUser(request, scopedUserId);
        ensureRunId(request);
        if (enforceSessionWrite) {
            sessions.assertWrite(request.getSessionId(), scopedUserId);
        }
        applyProjectAgent(request);
        return new OpsPreparedChatRequest(request);
    }

    private void bindUser(OpsAgentChatRequest request, String scopedUserId) {
        if (StringUtils.hasText(scopedUserId)) request.setUserId(scopedUserId);
    }

    private void ensureRunId(OpsAgentChatRequest request) {
        if (StringUtils.hasText(request.getRunId())) return;
        Object metadataRunId = request.getMetadata() == null
                ? null
                : request.getMetadata().get("runId");
        if (metadataRunId != null
                && StringUtils.hasText(String.valueOf(metadataRunId))) {
            request.setRunId(String.valueOf(metadataRunId).trim());
            return;
        }
        String sessionPart = StringUtils.hasText(request.getSessionId())
                ? request.getSessionId().trim()
                : "session";
        request.setRunId(
                "chat-" + sessionPart + "-"
                        + UUID.randomUUID().toString().substring(0, 8));
        if (request.getMetadata() == null) {
            request.setMetadata(new LinkedHashMap<>());
        }
        request.getMetadata().put("runId", request.getRunId());
    }

    private String requireProjectId(String projectId) {
        if (!StringUtils.hasText(projectId)) {
            throw new IllegalArgumentException("请先选择业务系统 projectId");
        }
        String normalized = projectId.trim();
        if (!projects.exists(normalized)) {
            throw new IllegalArgumentException("业务系统不存在：" + normalized);
        }
        return normalized;
    }

    private String requireDefaultAgent(String projectId) {
        String agentId = projects.defaultAgentId(projectId);
        if (!StringUtils.hasText(agentId)) {
            throw new IllegalArgumentException("项目 " + projectId + " 尚未配置默认 Agent");
        }
        return agentId;
    }
}
