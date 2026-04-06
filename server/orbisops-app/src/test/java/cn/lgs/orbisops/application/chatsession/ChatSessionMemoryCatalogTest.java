package cn.lgs.orbisops.application.chatsession;

import cn.lgs.orbisops.domain.chatsession.model.ChatSessionParticipantDraft;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionParticipantReplacement;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionSearchCriteria;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionSnapshot;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionUpdate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatSessionMemoryCatalogTest {

    @Test
    void saveCreatesOwnerAndSupportsFilteredSearch() {
        ChatSessionMemoryCatalog catalog = new ChatSessionMemoryCatalog();
        catalog.save(session("session-1", "owner", "agent-1", "慢 SQL 排查", true, 1L));
        catalog.save(session("session-2", "other", "agent-2", "指标分析", false, 1L));

        assertEquals("OWNER", catalog.activeParticipantRole("session-1", "owner").orElseThrow());
        assertEquals(1, catalog.search(new ChatSessionSearchCriteria(
                "owner", "agent-1", "慢 sql", true, 10)).size());
        assertTrue(catalog.search(new ChatSessionSearchCriteria(
                "stranger", "", "", null, 10)).isEmpty());
    }

    @Test
    void updateUsesStateVersionCasAndReturnsImmutableSnapshot() {
        ChatSessionMemoryCatalog catalog = new ChatSessionMemoryCatalog();
        catalog.save(session("session-1", "owner", "agent-1", "old", false, 1L));

        ChatSessionSnapshot updated = catalog.update(new ChatSessionUpdate(
                "session-1", "new", "ACTIVE", Map.of("favorite", true), 1L)).orElseThrow();

        assertEquals(2L, updated.stateVersion());
        assertEquals("new", updated.title());
        assertEquals(true, updated.metadata().get("favorite"));
        assertThrows(UnsupportedOperationException.class,
                () -> updated.metadata().put("forged", true));
        assertThrows(IllegalStateException.class,
                () -> catalog.update(new ChatSessionUpdate(
                        "session-1", "stale", "ACTIVE", Map.of(), 1L)));
    }

    @Test
    void participantReplacementIsAtomicAndIncrementsSessionVersion() {
        ChatSessionMemoryCatalog catalog = new ChatSessionMemoryCatalog();
        catalog.save(session("session-1", "owner", "agent-1", "title", false, 1L));

        boolean replaced = catalog.replaceParticipants(new ChatSessionParticipantReplacement(
                "session-1",
                "project-1",
                "owner",
                1L,
                List.of(
                        new ChatSessionParticipantDraft("editor", "EDITOR"),
                        new ChatSessionParticipantDraft("observer", "OBSERVER"))));

        assertTrue(replaced);
        assertEquals(List.of("OWNER", "EDITOR", "OBSERVER"),
                catalog.participants("session-1").stream().map(item -> item.role()).toList());
        assertEquals(2L, catalog.find("session-1").orElseThrow().stateVersion());
        assertFalse(catalog.replaceParticipants(new ChatSessionParticipantReplacement(
                "session-1", "project-1", "owner", 1L, List.of())));
    }

    @Test
    void touchAndRemoveStayInsideCatalogBoundary() {
        ChatSessionMemoryCatalog catalog = new ChatSessionMemoryCatalog();
        catalog.save(session("session-1", "owner", "agent-1", "old", false, 1L));

        catalog.touch("session-1", "new", "last message");

        assertEquals("new", catalog.find("session-1").orElseThrow().title());
        assertEquals("last message", catalog.find("session-1").orElseThrow().lastMessage());
        catalog.remove("session-1");
        assertTrue(catalog.find("session-1").isEmpty());
        assertTrue(catalog.participants("session-1").isEmpty());
    }

    private ChatSessionSnapshot session(String sessionId,
                                        String userId,
                                        String agentId,
                                        String title,
                                        boolean favorite,
                                        long stateVersion) {
        return new ChatSessionSnapshot(
                sessionId,
                userId,
                "project-1",
                agentId,
                "LATEST_PUBLISHED",
                1,
                "a".repeat(64),
                title,
                "AGENT",
                "GRAPH",
                false,
                "",
                "ACTIVE",
                stateVersion,
                Map.of("favorite", favorite),
                "2026-07-21 10:00:00",
                "2026-07-21 10:00:00",
                0,
                "");
    }
}
