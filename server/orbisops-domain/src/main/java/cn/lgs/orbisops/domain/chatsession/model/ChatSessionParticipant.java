package cn.lgs.orbisops.domain.chatsession.model;

/** Participant ACL row projected from the authoritative Chat Session store. */
public record ChatSessionParticipant(
        String userId,
        String role,
        String status,
        long stateVersion,
        String addedBy,
        String createdAt) {
}
