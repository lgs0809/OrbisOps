package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.ContextMemoryView;
import cn.lgs.orbisops.application.memory.MemoryMessageView;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMemoryMessage;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsMemoryRetrievalMapperTest {

    private final OpsMemoryRetrievalMapper mapper = new OpsMemoryRetrievalMapper();

    @Test
    void mapsMessageRoundTripThroughTypedReadModel() {
        OpsMemoryMessage message = OpsMemoryMessage.builder()
                .sessionId("s1")
                .userId("u1")
                .role("user")
                .content("hello")
                .createdAt("2026-07-21 18:00:00")
                .metadata(Map.of("turn_index", 3))
                .build();

        MemoryMessageView view = mapper.view(message);
        OpsMemoryMessage restored = mapper.message(view);

        assertEquals("hello", view.content());
        assertEquals("user", restored.getRole());
        assertEquals(3, restored.safeMetadata().get("turn_index"));
        assertThrows(UnsupportedOperationException.class,
                () -> view.metadata().put("new", "value"));
    }

    @Test
    void mapsContextMemoryMapToTypedView() {
        ContextMemoryView view = mapper.context(Map.of(
                "memoryId", "ctx-1",
                "memoryType", "PROJECT_CONTEXT",
                "scopeType", "PROJECT",
                "scopeId", "demo-project",
                "title", "Architecture",
                "summary", "DDD migration",
                "content", "Agent system DDD migration",
                "sourceMessageHash", "hash"));

        assertEquals("ctx-1", view.memoryId());
        assertEquals("demo-project", view.scopeId());
        assertEquals("DDD migration", view.summary());
    }
}
