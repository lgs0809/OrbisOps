package cn.lgs.orbisops.domain.chatsession.service;

import cn.lgs.orbisops.domain.chatsession.model.ChatSessionParticipantDraft;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionParticipantRole;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Domain policy for participant normalization and ACL decisions. */
public class ChatSessionParticipantPolicy {

    public List<ChatSessionParticipantDraft> normalize(List<ChatSessionParticipantDraft> participants,
                                                       String ownerUserId) {
        String owner = text(ownerUserId);
        Map<String, ChatSessionParticipantDraft> normalized = new LinkedHashMap<>();
        for (ChatSessionParticipantDraft participant : participants == null
                ? List.<ChatSessionParticipantDraft>of()
                : participants) {
            if (participant == null || !hasText(participant.userId())) continue;
            String userId = participant.userId().trim();
            if (userId.equals(owner)) continue;
            ChatSessionParticipantRole role = ChatSessionParticipantRole.requireManaged(participant.role());
            normalized.put(userId, new ChatSessionParticipantDraft(userId, role.name()));
        }
        return List.copyOf(normalized.values());
    }

    public boolean canRead(String ownerUserId,
                           String actor,
                           Optional<String> activeParticipantRole) {
        if (!hasText(actor)) return false;
        if (actor.trim().equals(text(ownerUserId))) return true;
        return activeParticipantRole != null && activeParticipantRole.isPresent();
    }

    public boolean canWrite(String ownerUserId,
                            String actor,
                            Optional<String> activeParticipantRole) {
        if (!hasText(actor)) return false;
        if (actor.trim().equals(text(ownerUserId))) return true;
        return activeParticipantRole != null
                && activeParticipantRole
                .map(ChatSessionParticipantRole::require)
                .map(ChatSessionParticipantRole::writable)
                .orElse(false);
    }

    public void requireOwner(String ownerUserId, String actor) {
        if (!hasText(actor) || !actor.trim().equals(text(ownerUserId))) {
            throw new SecurityException("SESSION_PARTICIPANT_MANAGE_FORBIDDEN");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
