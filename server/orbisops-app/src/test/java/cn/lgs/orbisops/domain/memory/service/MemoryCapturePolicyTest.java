package cn.lgs.orbisops.domain.memory.service;

import cn.lgs.orbisops.domain.memory.model.CapturedMemoryMessage;
import cn.lgs.orbisops.domain.memory.model.MemoryCaptureDraft;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemoryCapturePolicyTest {

    private final MemoryCapturePolicy policy = new MemoryCapturePolicy();

    @Test
    void appliesRoleTurnTimestampAndActiveDefaults() {
        CapturedMemoryMessage message = policy.prepare(
                new MemoryCaptureDraft(
                        " session-1 ",
                        " user-1 ",
                        " ",
                        "captured content",
                        Map.of("projectId", "demo-project")),
                3L,
                1_753_093_800_000L,
                "2026-07-21 18:30:00");

        assertEquals("session-1", message.sessionId());
        assertEquals("user-1", message.userId());
        assertEquals("assistant", message.role());
        assertEquals(3L, message.metadata().get("turn_index"));
        assertEquals(1_753_093_800_000L, message.metadata().get("created_at_epoch_ms"));
        assertEquals("ACTIVE", message.metadata().get("memory_status"));
        assertEquals("demo-project", message.metadata().get("projectId"));
        assertThrows(UnsupportedOperationException.class,
                () -> message.metadata().put("new", "value"));
    }

    @Test
    void preservesExplicitLifecycleMetadata() {
        CapturedMemoryMessage message = policy.prepare(
                new MemoryCaptureDraft(
                        "s1",
                        "u1",
                        " user ",
                        "content",
                        Map.of(
                                "turn_index", 99,
                                "created_at_epoch_ms", 123L,
                                "memory_status", "PINNED")),
                2L,
                456L,
                "2026-07-21 18:30:00");

        assertEquals("user", message.role());
        assertEquals(99, message.metadata().get("turn_index"));
        assertEquals(123L, message.metadata().get("created_at_epoch_ms"));
        assertEquals("PINNED", message.metadata().get("memory_status"));
    }

    @Test
    void rejectsInvalidDraft() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.prepare(
                        new MemoryCaptureDraft(" ", "u1", "user", "content", Map.of()),
                        1L,
                        1L,
                        ""));
        assertThrows(IllegalArgumentException.class,
                () -> policy.prepare(null, 1L, 1L, ""));
    }
}
