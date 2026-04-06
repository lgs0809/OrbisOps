package cn.lgs.orbisops.domain.chatsession.adapter.repository;

import cn.lgs.orbisops.domain.chatsession.model.ChatMessageSnapshot;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionParticipant;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionParticipantReplacement;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionSearchCriteria;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionSnapshot;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionUpdate;

import java.util.List;
import java.util.Optional;

/** Authoritative persistence boundary for Chat Session state, ACL and message read models. */
public interface IChatSessionRepository {

    boolean available();

    void insert(ChatSessionSnapshot session);

    void insertIfAbsent(ChatSessionSnapshot session);

    Optional<ChatSessionSnapshot> findById(String sessionId);

    List<ChatSessionSnapshot> search(ChatSessionSearchCriteria criteria);

    List<ChatMessageSnapshot> messages(String sessionId, int limit);

    boolean update(ChatSessionUpdate update);

    boolean hasActiveParticipant(String sessionId, String userId);

    Optional<String> activeParticipantRole(String sessionId, String userId);

    List<ChatSessionParticipant> participants(String sessionId);

    boolean replaceParticipants(ChatSessionParticipantReplacement replacement);

    boolean markDeleted(String sessionId);

    void touch(String sessionId, String title);
}
