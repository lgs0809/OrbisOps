package cn.lgs.orbisops.application.chatsession;

import cn.lgs.orbisops.domain.chatsession.model.ChatSessionParticipant;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionParticipantDraft;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionParticipantReplacement;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionSearchCriteria;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionSnapshot;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionUpdate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Explicit non-production fallback catalog for Chat Session state and participant ACL. */
public class ChatSessionMemoryCatalog {

    private final Map<String, ChatSessionSnapshot> sessions = new ConcurrentHashMap<>();
    private final Map<String, Map<String, ChatSessionParticipant>> participants = new ConcurrentHashMap<>();

    public void save(ChatSessionSnapshot session) {
        if (session == null || !hasText(session.sessionId())) return;
        sessions.put(session.sessionId(), session);
        ensureOwner(session);
    }

    public Optional<ChatSessionSnapshot> find(String sessionId) {
        return Optional.ofNullable(sessions.get(value(sessionId)));
    }

    public List<ChatSessionSnapshot> search(ChatSessionSearchCriteria criteria) {
        ChatSessionSearchCriteria safe = criteria == null
                ? new ChatSessionSearchCriteria("", "", "", null, 100)
                : criteria;
        return sessions.values().stream()
                .filter(session -> !"DELETED".equalsIgnoreCase(value(session.status())))
                .filter(session -> !hasText(safe.userId()) || canRead(session, safe.userId()))
                .filter(session -> !hasText(safe.agentId()) || safe.agentId().equals(session.agentId()))
                .filter(session -> safe.favorite() == null || favorite(session) == safe.favorite())
                .filter(session -> !hasText(safe.keyword()) || matches(session, safe.keyword()))
                .sorted(Comparator.comparing(
                        ChatSessionSnapshot::lastActiveAt,
                        Comparator.nullsLast(String::compareTo)).reversed())
                .limit(safe.limit())
                .toList();
    }

    public Optional<ChatSessionSnapshot> update(ChatSessionUpdate update) {
        if (update == null || !hasText(update.sessionId())) return Optional.empty();
        synchronized (sessions) {
            ChatSessionSnapshot current = sessions.get(update.sessionId());
            if (current == null) return Optional.empty();
            if (update.expectedStateVersion() <= 0L
                    || current.stateVersion() != update.expectedStateVersion()) {
                throw new IllegalStateException("SESSION_VERSION_CONFLICT：请刷新会话后重试");
            }
            ChatSessionSnapshot changed = copy(
                    current,
                    hasText(update.title()) ? update.title() : current.title(),
                    hasText(update.status()) ? update.status() : current.status(),
                    update.metadata(),
                    current.stateVersion() + 1L,
                    current.lastMessage());
            sessions.put(current.sessionId(), changed);
            return Optional.of(changed);
        }
    }

    public boolean replaceParticipants(ChatSessionParticipantReplacement replacement) {
        if (replacement == null || !hasText(replacement.sessionId())) return false;
        synchronized (sessions) {
            ChatSessionSnapshot current = sessions.get(replacement.sessionId());
            if (current == null
                    || current.stateVersion() != replacement.expectedSessionVersion()
                    || !value(current.userId()).equals(value(replacement.ownerUserId()))) {
                return false;
            }
            Map<String, ChatSessionParticipant> values = new LinkedHashMap<>();
            values.put(current.userId(), participant(current.userId(), "OWNER", replacement.ownerUserId()));
            for (ChatSessionParticipantDraft draft : replacement.participants()) {
                values.put(draft.userId(), participant(draft.userId(), draft.role(), replacement.ownerUserId()));
            }
            participants.put(current.sessionId(), new ConcurrentHashMap<>(values));
            sessions.put(current.sessionId(), copy(
                    current,
                    current.title(),
                    current.status(),
                    current.metadata(),
                    current.stateVersion() + 1L,
                    current.lastMessage()));
            return true;
        }
    }

    public Optional<String> activeParticipantRole(String sessionId, String userId) {
        ChatSessionParticipant participant = participants
                .getOrDefault(value(sessionId), Map.of())
                .get(value(userId));
        if (participant == null || !"ACTIVE".equals(participant.status())) return Optional.empty();
        return Optional.of(participant.role());
    }

    public List<ChatSessionParticipant> participants(String sessionId) {
        List<ChatSessionParticipant> values = new ArrayList<>(participants
                .getOrDefault(value(sessionId), Map.of())
                .values());
        values.sort(Comparator.comparingInt(item -> roleOrder(item.role())));
        return List.copyOf(values);
    }

    public void touch(String sessionId, String title, String lastMessage) {
        if (!hasText(sessionId)) return;
        synchronized (sessions) {
            ChatSessionSnapshot current = sessions.get(sessionId);
            if (current == null) return;
            sessions.put(sessionId, copy(
                    current,
                    hasText(title) ? title : current.title(),
                    current.status(),
                    current.metadata(),
                    current.stateVersion(),
                    hasText(lastMessage) ? lastMessage : current.lastMessage()));
        }
    }

    public void remove(String sessionId) {
        sessions.remove(value(sessionId));
        participants.remove(value(sessionId));
    }

    public int size() {
        return sessions.size();
    }

    private boolean canRead(ChatSessionSnapshot session, String actor) {
        if (value(actor).equals(value(session.userId()))) return true;
        return activeParticipantRole(session.sessionId(), actor).isPresent();
    }

    private boolean favorite(ChatSessionSnapshot session) {
        return session.favorite();
    }

    private boolean matches(ChatSessionSnapshot session, String keyword) {
        String normalized = value(keyword).toLowerCase();
        return contains(session.title(), normalized)
                || contains(session.lastMessage(), normalized)
                || contains(session.agentId(), normalized)
                || contains(session.mode(), normalized)
                || contains(session.sessionId(), normalized);
    }

    private boolean contains(String source, String keyword) {
        return hasText(source) && source.toLowerCase().contains(keyword);
    }

    private void ensureOwner(ChatSessionSnapshot session) {
        if (!hasText(session.userId())) return;
        participants.computeIfAbsent(session.sessionId(), ignored -> new ConcurrentHashMap<>())
                .put(session.userId(), participant(session.userId(), "OWNER", session.userId()));
    }

    private ChatSessionParticipant participant(String userId, String role, String addedBy) {
        return new ChatSessionParticipant(userId, role, "ACTIVE", 1L, addedBy, "");
    }

    private int roleOrder(String role) {
        if ("OWNER".equals(role)) return 0;
        if ("EDITOR".equals(role)) return 1;
        return 2;
    }

    private ChatSessionSnapshot copy(ChatSessionSnapshot current,
                                     String title,
                                     String status,
                                     Map<String, Object> metadata,
                                     long stateVersion,
                                     String lastMessage) {
        return new ChatSessionSnapshot(
                current.sessionId(),
                current.userId(),
                current.projectId(),
                current.agentId(),
                current.agentBindingMode(),
                current.agentVersion(),
                current.agentDefinitionHash(),
                title,
                current.mode(),
                current.engine(),
                current.ragEnabled(),
                current.knowledgeBaseId(),
                status,
                stateVersion,
                metadata,
                current.createdAt(),
                current.lastActiveAt(),
                current.messageCount(),
                lastMessage);
    }

    private boolean hasText(String input) {
        return input != null && !input.trim().isBlank();
    }

    private String value(String input) {
        return input == null ? "" : input.trim();
    }
}
