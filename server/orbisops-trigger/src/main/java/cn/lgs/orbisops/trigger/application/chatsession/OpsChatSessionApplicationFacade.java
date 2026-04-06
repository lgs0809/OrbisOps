package cn.lgs.orbisops.trigger.application.chatsession;

import cn.lgs.orbisops.application.chatsession.ChatSessionAccessUseCase;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.domain.runtime.graph.model.GraphEvent;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatResponse;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatMessageView;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatSession;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatSessionCreateRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatSessionService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatSessionUpdateRequest;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;

/** Trigger-facing Chat Session facade backed by typed Application and Domain boundaries. */
public final class OpsChatSessionApplicationFacade {

    private static final String GENERIC_REACT_MODE = "AGENT";

    private final OpsChatSessionService sessionService;
    private final ProjectDefinitionApplicationService projects;
    private final GraphEventApplicationService graphEvents;
    private final ChatSessionAccessUseCase accessUseCase;

    public OpsChatSessionApplicationFacade(
            OpsChatSessionService sessionService,
            ProjectDefinitionApplicationService projects,
            GraphEventApplicationService graphEvents) {
        if (sessionService == null || projects == null || graphEvents == null) {
            throw new IllegalArgumentException("CHAT_SESSION_FACADE_DEPENDENCIES_REQUIRED");
        }
        this.sessionService = sessionService;
        this.projects = projects;
        this.graphEvents = graphEvents;
        this.accessUseCase = new ChatSessionAccessUseCase(
                new OpsChatSessionAccessAdapter(sessionService));
    }

    public OpsChatSession create(
            OpsChatSessionCreateRequest request,
            String scopedUserId) {
        OpsChatSessionCreateRequest safeRequest = request == null
                ? new OpsChatSessionCreateRequest()
                : request;
        bindUser(safeRequest, scopedUserId);
        applyProjectDefaults(safeRequest);
        return sessionService.create(safeRequest);
    }

    public OpsChatSession update(
            String sessionId,
            OpsChatSessionUpdateRequest request,
            String actor) {
        accessUseCase.assertOwner(sessionId, actor);
        return sessionService.update(sessionId, request);
    }

    public List<OpsChatSessionService.SessionParticipant> participants(
            String sessionId,
            String actor) {
        return sessionService.participants(sessionId, actor);
    }

    public List<OpsChatSessionService.SessionParticipant> replaceParticipants(
            String sessionId,
            long expectedSessionVersion,
            List<OpsChatSessionService.SessionParticipantInput> participants,
            String actor) {
        return sessionService.replaceParticipants(
                sessionId,
                expectedSessionVersion,
                participants,
                actor);
    }

    public List<OpsChatSession> list(
            String requestedUserId,
            String agentId,
            String keyword,
            Boolean favorite,
            Integer limit,
            String scopedUserId) {
        String userId = StringUtils.hasText(scopedUserId)
                ? scopedUserId
                : requestedUserId;
        return sessionService.list(
                userId,
                agentId,
                keyword,
                favorite,
                limit == null ? 50 : limit);
    }

    public List<OpsChatMessageView> messages(
            String sessionId,
            Integer limit,
            String actor) {
        accessUseCase.assertRead(sessionId, actor);
        return sessionService.messages(sessionId, limit == null ? 200 : limit);
    }

    public List<GraphEvent> events(String sessionId, String actor) {
        accessUseCase.assertRead(sessionId, actor);
        if (!StringUtils.hasText(sessionId)) return List.of();
        return graphEvents.list(sessionId.trim());
    }

    public boolean delete(String sessionId, String actor) {
        accessUseCase.assertOwner(sessionId, actor);
        return sessionService.delete(sessionId);
    }

    public OpsChatSession ensureForChat(OpsAgentChatRequest request) {
        return sessionService.ensureForChat(request);
    }

    public void assertWrite(String sessionId, String actor) {
        accessUseCase.assertWrite(sessionId, actor);
    }

    public void touch(OpsChatSession session, OpsAgentChatResponse response) {
        if (session != null && response != null) {
            sessionService.touch(
                    session.getSessionId(),
                    session.getTitle(),
                    response.getContent());
        }
    }

    private void bindUser(
            OpsChatSessionCreateRequest request,
            String scopedUserId) {
        if (request != null && StringUtils.hasText(scopedUserId)) {
            request.setUserId(scopedUserId);
        }
    }

    private void applyProjectDefaults(OpsChatSessionCreateRequest request) {
        if (request == null || "SIMPLE".equalsIgnoreCase(request.getMode())) return;
        String projectId = requireProjectId(request.getProjectId());
        if (!StringUtils.hasText(request.getAgentId())) {
            request.setAgentId(requireDefaultAgent(projectId));
        }
        request.setMode(GENERIC_REACT_MODE);
        if (request.getMetadata() == null) {
            request.setMetadata(new LinkedHashMap<>());
        }
        request.getMetadata().put("projectId", projectId);
        request.getMetadata().put("defaultProjectAgent", true);
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
