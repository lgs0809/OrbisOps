package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.MemorySelectionReference;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OpsMemorySelectionReferenceMapperTest {

    private final OpsMemorySelectionReferenceMapper mapper = new OpsMemorySelectionReferenceMapper();

    @Test
    void mapsTypedReferenceToCompatibilityMap() {
        MemorySelectionReference reference = new MemorySelectionReference(
                "ctx-1",
                1,
                "memory-hash",
                "PROJECT_CONTEXT",
                "PROJECT",
                "demo-project",
                "source-hash",
                "content-hash",
                "2026-07-21T10:45:00Z",
                false);

        Map<String, Object> view = mapper.view(reference);

        assertEquals("ctx-1", view.get("memoryId"));
        assertEquals(1, view.get("version"));
        assertEquals("memory-hash", view.get("memoryHash"));
        assertEquals("PROJECT_CONTEXT", view.get("memoryType"));
        assertEquals("PROJECT", view.get("scope"));
        assertEquals("demo-project", view.get("scopeId"));
        assertEquals("source-hash", view.get("sourceMessageHash"));
        assertEquals("content-hash", view.get("contentHash"));
        assertEquals("2026-07-21T10:45:00Z", view.get("selectedAt"));
        assertEquals(false, view.get("verified"));
    }

    @Test
    void mapsListsAndHandlesNulls() {
        MemorySelectionReference reference = new MemorySelectionReference(
                "ctx-1", 1, "hash", "TYPE", "SCOPE", "id", "source", "hash", "time", false);

        List<Map<String, Object>> views = mapper.views(java.util.Arrays.asList(null, reference));

        assertEquals(1, views.size());
        assertFalse(views.get(0).isEmpty());
        assertEquals(Map.of(), mapper.view(null));
        assertEquals(List.of(), mapper.views(null));
    }
}
