package cn.lgs.orbisops.domain.chatsession;

import cn.lgs.orbisops.domain.chatsession.model.ChatSessionAgentBindingMode;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionParticipantDraft;
import cn.lgs.orbisops.domain.chatsession.service.ChatSessionAgentBindingPolicy;
import cn.lgs.orbisops.domain.chatsession.service.ChatSessionParticipantPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatSessionPolicyTest {

    private final ChatSessionAgentBindingPolicy bindingPolicy = new ChatSessionAgentBindingPolicy();
    private final ChatSessionParticipantPolicy participantPolicy = new ChatSessionParticipantPolicy();

    @Test
    void bindingModeDefaultsAndPinnedVersionAreDomainRules() {
        assertEquals(ChatSessionAgentBindingMode.LATEST_PUBLISHED,
                ChatSessionAgentBindingMode.resolve(null, Map.of(), null));
        assertEquals(ChatSessionAgentBindingMode.PINNED_VERSION,
                ChatSessionAgentBindingMode.resolve(null, Map.of(), 3));
        assertEquals(ChatSessionAgentBindingMode.PINNED_VERSION,
                ChatSessionAgentBindingMode.resolve("pinned_version", Map.of(), 3));
        assertThrows(IllegalArgumentException.class,
                () -> ChatSessionAgentBindingMode.resolve("PINNED_VERSION", Map.of(), null));
        assertThrows(IllegalArgumentException.class,
                () -> ChatSessionAgentBindingMode.resolve("FLOATING", Map.of(), null));
    }

    @Test
    void resolvedAgentAndPinnedHashMustBeCompleteAndStable() {
        bindingPolicy.requireResolved("agent-1", 3, "a".repeat(64));
        assertThrows(IllegalStateException.class,
                () -> bindingPolicy.requireResolved("agent-1", null, ""));
        assertThrows(SecurityException.class,
                () -> bindingPolicy.verifyPinnedHash(
                        ChatSessionAgentBindingMode.PINNED_VERSION,
                        "stored",
                        "changed"));
        bindingPolicy.verifyPinnedHash(
                ChatSessionAgentBindingMode.LATEST_PUBLISHED,
                "stored",
                "changed");
    }

    @Test
    void participantNormalizationRemovesOwnerDeduplicatesAndValidatesRole() {
        List<ChatSessionParticipantDraft> normalized = participantPolicy.normalize(List.of(
                new ChatSessionParticipantDraft("owner", "EDITOR"),
                new ChatSessionParticipantDraft("editor", "editor"),
                new ChatSessionParticipantDraft("editor", "OBSERVER"),
                new ChatSessionParticipantDraft("observer", null)), "owner");

        assertEquals(List.of(
                new ChatSessionParticipantDraft("editor", "OBSERVER"),
                new ChatSessionParticipantDraft("observer", "OBSERVER")), normalized);
        assertThrows(IllegalArgumentException.class,
                () -> participantPolicy.normalize(List.of(
                        new ChatSessionParticipantDraft("other", "OWNER")), "owner"));
    }

    @Test
    void participantReadAndWriteRightsBelongToDomainPolicy() {
        assertTrue(participantPolicy.canRead("owner", "owner", Optional.empty()));
        assertTrue(participantPolicy.canRead("owner", "observer", Optional.of("OBSERVER")));
        assertFalse(participantPolicy.canRead("owner", "stranger", Optional.empty()));
        assertTrue(participantPolicy.canWrite("owner", "editor", Optional.of("EDITOR")));
        assertFalse(participantPolicy.canWrite("owner", "observer", Optional.of("OBSERVER")));
        assertThrows(SecurityException.class,
                () -> participantPolicy.requireOwner("owner", "editor"));
    }
}
