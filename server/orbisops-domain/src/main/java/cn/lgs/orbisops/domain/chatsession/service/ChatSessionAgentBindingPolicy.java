package cn.lgs.orbisops.domain.chatsession.service;

import cn.lgs.orbisops.domain.chatsession.model.ChatSessionAgentBindingMode;

/** Domain invariants for resolved Agent versions attached to Chat Sessions. */
public class ChatSessionAgentBindingPolicy {

    public void requireResolved(String agentId, Integer version, String definitionHash) {
        if (!hasText(agentId) || version == null || version <= 0 || !hasText(definitionHash)) {
            throw new IllegalStateException("CHAT_SESSION_AGENT_VERSION_INCOMPLETE");
        }
    }

    public void verifyPinnedHash(ChatSessionAgentBindingMode mode,
                                 String storedDefinitionHash,
                                 String resolvedDefinitionHash) {
        if (mode == ChatSessionAgentBindingMode.PINNED_VERSION
                && hasText(storedDefinitionHash)
                && !storedDefinitionHash.equals(resolvedDefinitionHash)) {
            throw new SecurityException("CHAT_SESSION_AGENT_DEFINITION_HASH_MISMATCH");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
