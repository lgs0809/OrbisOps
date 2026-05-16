package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.chatsession.ChatSessionStoreApplicationService;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionParticipant;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionParticipantDraft;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionParticipantReplacement;
import cn.lgs.orbisops.domain.chatsession.service.ChatSessionParticipantPolicy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/** Enforces Chat Session participant ACL and replacement CAS. */
@Component
public final class OpsChatSessionParticipantCoordinator {

    private final ChatSessionStoreApplicationService sessionStore;
    private final ChatSessionParticipantPolicy participantPolicy;

    public OpsChatSessionParticipantCoordinator(
            ChatSessionStoreApplicationService sessionStore) {
        if (sessionStore == null) {
            throw new IllegalArgumentException("CHAT_SESSION_STORE_REQUIRED");
        }
        this.sessionStore = sessionStore;
        this.participantPolicy = new ChatSessionParticipantPolicy();
    }

    public boolean canRead(OpsChatSession session, String actor) {
        if (session == null || !StringUtils.hasText(actor)) {
            return false;
        }
        String normalizedActor = actor.trim();
        return participantPolicy.canRead(
                session.getUserId(),
                normalizedActor,
                sessionStore.activeParticipantRole(
                        session.getSessionId(), normalizedActor));
    }

    public boolean canWrite(OpsChatSession session, String actor) {
        if (session == null || !StringUtils.hasText(actor)) {
            return false;
        }
        String normalizedActor = actor.trim();
        return participantPolicy.canWrite(
                session.getUserId(),
                normalizedActor,
                sessionStore.activeParticipantRole(
                        session.getSessionId(), normalizedActor));
    }

    public List<ChatSessionParticipant> participants(
            OpsChatSession session,
            String actor) {
        if (!canRead(session, actor)) {
            throw new SecurityException("SESSION_READ_FORBIDDEN");
        }
        return sessionStore.participants(session.getSessionId());
    }

    public List<ChatSessionParticipant> replace(
            OpsChatSession session,
            long expectedSessionVersion,
            List<ChatSessionParticipantDraft> drafts,
            String actor) {
        if (session == null) {
            throw new IllegalArgumentException("会话不存在");
        }
        participantPolicy.requireOwner(session.getUserId(), actor);
        if (expectedSessionVersion <= 0L) {
            throw new IllegalArgumentException("expectedSessionVersion 必须大于 0");
        }
        List<ChatSessionParticipantDraft> normalized = participantPolicy.normalize(
                drafts == null ? List.of() : drafts,
                session.getUserId());
        ChatSessionParticipantReplacement replacement =
                new ChatSessionParticipantReplacement(
                        session.getSessionId(),
                        value(session.getProjectId()),
                        actor,
                        expectedSessionVersion,
                        normalized);
        if (!sessionStore.replaceParticipants(replacement)) {
            throw new IllegalStateException(
                    "SESSION_VERSION_CONFLICT：参与者列表已变化");
        }
        return participants(session, actor);
    }

    private String value(String value) {
        return StringUtils.hasText(value) ? value : "";
    }
}
