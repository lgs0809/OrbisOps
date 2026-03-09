package cn.lgs.orbisops.domain.chatsession.model;

import java.util.List;

/** Atomic participant replacement guarded by the Chat Session state version. */
public record ChatSessionParticipantReplacement(
        String sessionId,
        String projectId,
        String ownerUserId,
        long expectedSessionVersion,
        List<ChatSessionParticipantDraft> participants) {

    public ChatSessionParticipantReplacement {
        participants = participants == null ? List.of() : List.copyOf(participants);
    }
}
