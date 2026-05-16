package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.chatsession.ChatSessionStoreApplicationService;
import cn.lgs.orbisops.application.chatsession.ChatSessionTransactionPort;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionParticipant;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionParticipantDraft;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionSearchCriteria;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionUpdate;
import cn.lgs.orbisops.trigger.application.chatsession.OpsChatSessionMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Compatibility facade for Chat Session lifecycle, binding and participant use cases. */
@Service
public final class OpsChatSessionService {

    private final ChatSessionStoreApplicationService sessionStore;
    private final ChatSessionTransactionPort transactionPort;
    private final OpsMemoryFacade memoryFacade;
    private final OpsChatSessionAgentBinder agentBinder;
    private final OpsChatSessionFactory sessionFactory;
    private final OpsChatSessionParticipantCoordinator participantCoordinator;
    private final OpsChatSessionMapper sessionMapper;

    public OpsChatSessionService(
            ChatSessionStoreApplicationService sessionStore,
            ChatSessionTransactionPort transactionPort,
            OpsMemoryFacade memoryFacade,
            OpsChatSessionAgentBinder agentBinder,
            OpsChatSessionFactory sessionFactory,
            OpsChatSessionParticipantCoordinator participantCoordinator) {
        if (sessionStore == null
                || transactionPort == null
                || memoryFacade == null
                || agentBinder == null
                || sessionFactory == null
                || participantCoordinator == null) {
            throw new IllegalArgumentException("CHAT_SESSION_DEPENDENCIES_REQUIRED");
        }
        this.sessionStore = sessionStore;
        this.transactionPort = transactionPort;
        this.memoryFacade = memoryFacade;
        this.agentBinder = agentBinder;
        this.sessionFactory = sessionFactory;
        this.participantCoordinator = participantCoordinator;
        this.sessionMapper = new OpsChatSessionMapper();
    }

    public OpsChatSession create(OpsChatSessionCreateRequest request) {
        OpsChatSessionCreateRequest safeRequest = request == null
                ? new OpsChatSessionCreateRequest()
                : request;
        OpsChatSessionAgentBinder.Binding binding = agentBinder.resolve(safeRequest);
        OpsChatSession session = sessionFactory.create(safeRequest, binding);
        return sessionMapper.view(
                sessionStore.insert(sessionMapper.snapshot(session)));
    }

    public OpsChatSession ensureForChat(OpsAgentChatRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("chat request 不能为空");
        }
        if (!StringUtils.hasText(request.getSessionId())) {
            OpsChatSession session = create(sessionFactory.createRequest(request));
            request.setSessionId(session.getSessionId());
            agentBinder.bindRequestToSession(request, session);
            return session;
        }
        Optional<OpsChatSession> existing = get(request.getSessionId());
        if (existing.isPresent()) {
            verifyProjectBoundary(request, existing.get());
            touch(
                    request.getSessionId(),
                    sessionFactory.touchTitle(
                            existing.get().getTitle(), request.getQuery()),
                    null);
            agentBinder.bindRequestToSession(request, existing.get());
            return existing.get();
        }
        OpsChatSessionAgentBinder.Binding binding = agentBinder.resolve(request);
        OpsChatSession session = sessionFactory.restoreMissing(request, binding);
        sessionStore.insertIfAbsent(sessionMapper.snapshot(session));
        agentBinder.bindRequestToSession(request, session);
        return session;
    }

    public Optional<OpsChatSession> get(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return Optional.empty();
        }
        return sessionStore.find(sessionId).map(sessionMapper::view);
    }

    public List<OpsChatSession> list(
            String userId,
            String agentId,
            int limit) {
        return list(userId, agentId, null, null, limit);
    }

    public List<OpsChatSession> list(
            String userId,
            String agentId,
            String keyword,
            Boolean favorite,
            int limit) {
        ChatSessionSearchCriteria criteria = new ChatSessionSearchCriteria(
                userId,
                agentId,
                value(keyword).trim().toLowerCase(),
                favorite,
                limit);
        return sessionStore.search(criteria).stream()
                .map(sessionMapper::view)
                .toList();
    }

    public List<OpsChatMessageView> messages(String sessionId, int limit) {
        if (!StringUtils.hasText(sessionId)) {
            return List.of();
        }
        return sessionStore.messages(sessionId, limit).stream()
                .map(sessionMapper::view)
                .toList();
    }

    public OpsChatSession update(
            String sessionId,
            OpsChatSessionUpdateRequest request) {
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        OpsChatSessionUpdateRequest safeRequest = request == null
                ? new OpsChatSessionUpdateRequest()
                : request;
        Long expectedVersion = safeRequest.getExpectedStateVersion();
        if (expectedVersion == null || expectedVersion <= 0L) {
            throw new IllegalArgumentException(
                    "更新会话必须提供 expectedStateVersion");
        }
        ChatSessionUpdate update = new ChatSessionUpdate(
                sessionId,
                value(safeRequest.getTitle()),
                value(safeRequest.getStatus()),
                safeRequest.getMetadata() == null
                        ? Map.of()
                        : safeRequest.getMetadata(),
                expectedVersion);
        return sessionStore.update(update)
                .map(sessionMapper::view)
                .orElse(null);
    }

    public boolean canRead(String sessionId, String actor) {
        return participantCoordinator.canRead(
                get(sessionId).orElse(null), actor);
    }

    public boolean canWrite(String sessionId, String actor) {
        return participantCoordinator.canWrite(
                get(sessionId).orElse(null), actor);
    }

    public List<SessionParticipant> participants(
            String sessionId,
            String actor) {
        OpsChatSession session = get(sessionId).orElse(null);
        return participantCoordinator.participants(session, actor).stream()
                .map(this::view)
                .toList();
    }

    public List<SessionParticipant> replaceParticipants(
            String sessionId,
            long expectedSessionVersion,
            List<SessionParticipantInput> participants,
            String actor) {
        return transactionPort.required(() -> replaceParticipantsInTransaction(
                sessionId,
                expectedSessionVersion,
                participants,
                actor));
    }

    private List<SessionParticipant> replaceParticipantsInTransaction(
            String sessionId,
            long expectedSessionVersion,
            List<SessionParticipantInput> participants,
            String actor) {
        OpsChatSession session = get(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        List<ChatSessionParticipantDraft> drafts = participants == null
                ? List.of()
                : participants.stream()
                .map(item -> item == null
                        ? null
                        : new ChatSessionParticipantDraft(
                        item.userId(), item.role()))
                .toList();
        return participantCoordinator.replace(
                        session,
                        expectedSessionVersion,
                        drafts,
                        actor)
                .stream()
                .map(this::view)
                .toList();
    }

    public boolean delete(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return false;
        }
        memoryFacade.clear(sessionId);
        return sessionStore.markDeleted(sessionId);
    }

    public void touch(String sessionId, String title, String lastMessage) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        sessionStore.touch(
                sessionId,
                value(title),
                sessionFactory.messageSummary(lastMessage));
    }

    private void verifyProjectBoundary(
            OpsAgentChatRequest request,
            OpsChatSession session) {
        if (StringUtils.hasText(request.getProjectId())
                && StringUtils.hasText(session.getProjectId())
                && !request.getProjectId().trim()
                .equals(session.getProjectId().trim())) {
            throw new IllegalArgumentException(
                    "会话属于项目 " + session.getProjectId()
                            + "，不能切换到项目 " + request.getProjectId());
        }
    }

    private SessionParticipant view(ChatSessionParticipant participant) {
        return new SessionParticipant(
                participant.userId(),
                participant.role(),
                participant.status(),
                participant.stateVersion(),
                participant.addedBy(),
                participant.createdAt());
    }

    private String value(String value) {
        return StringUtils.hasText(value) ? value : "";
    }

    public record SessionParticipant(
            String userId,
            String role,
            String status,
            long stateVersion,
            String addedBy,
            String createdAt) {
    }

    public record SessionParticipantInput(String userId, String role) {
    }
}
