package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.domain.memory.model.ContextMemorySnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsContextMemoryMapperTest {

    private final OpsContextMemoryMapper mapper = new OpsContextMemoryMapper();

    @Test
    void mapsTypedSnapshotToCompatibilityMap() {
        Map<String, Object> view = mapper.view(snapshot());

        assertEquals("ctx-1", view.get("memoryId"));
        assertEquals("PROJECT", view.get("scopeType"));
        assertEquals("PROJECT_CONTEXT", view.get("memoryType"));
        assertEquals(BigDecimal.valueOf(0.9D), view.get("confidence"));
        assertEquals("2026-07-21 20:00:00", view.get("updateTime"));
    }

    @Test
    void serializesStructuredKeywordsAndHandlesNulls() {
        String keywords = mapper.keywords(List.of("ddd", "memory"));

        assertTrue(keywords.contains("ddd"));
        assertEquals("plain", mapper.keywords("plain"));
        assertEquals("", mapper.keywords(null));
        assertEquals(Map.of(), mapper.view(null));
        assertEquals(List.of(), mapper.views(null));
    }

    private ContextMemorySnapshot snapshot() {
        return new ContextMemorySnapshot(
                1L,
                "ctx-1",
                "PROJECT",
                "demo-project",
                "PROJECT_CONTEXT",
                "DDD migration",
                "summary",
                "content",
                "[]",
                "ACTIVE",
                BigDecimal.valueOf(0.9D),
                "memory_extractor",
                "session-1",
                "source-hash",
                "user-1",
                "2026-07-21 19:00:00",
                "2026-07-21 20:00:00",
                "");
    }
}
