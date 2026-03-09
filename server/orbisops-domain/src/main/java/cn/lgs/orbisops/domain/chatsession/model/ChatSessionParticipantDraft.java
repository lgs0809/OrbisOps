package cn.lgs.orbisops.domain.chatsession.model;

/** Normalized participant input accepted by the repository replacement transaction. */
public record ChatSessionParticipantDraft(String userId, String role) {
}
