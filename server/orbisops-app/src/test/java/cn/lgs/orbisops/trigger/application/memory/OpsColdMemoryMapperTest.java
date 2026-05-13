package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import cn.lgs.orbisops.domain.memory.model.MemoryItemCandidate;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMemoryItem;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMemoryMessage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsColdMemoryMapperTest {

    private final OpsColdMemoryMapper mapper = new OpsColdMemoryMapper();

    @Test
    void messageMapsToImmutableDomainSnapshot() {
        OpsMemoryMessage message = OpsMemoryMessage.builder()
                .sessionId("session-1")
                .userId("user-1")
                .role("user")
                .content("hello")
                .createdAt("2026-07-21 10:00:00")
                .metadata(Map.of("turn_index", 1))
                .build();

        ColdMemoryMessageSnapshot snapshot = mapper.snapshot(message);

        assertEquals("session-1", snapshot.sessionId());
        assertEquals("hello", snapshot.content());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.metadata().put("forged", true));
    }

    @Test
    void memoryItemRoundTripPreservesDurableFields() {
        OpsMemoryItem item = OpsMemoryItem.builder()
                .sessionId("session-1")
                .userId("user-1")
                .memoryType("fact")
                .content("memory")
                .importance(BigDecimal.valueOf(0.8))
                .tagsJson("[]")
                .sourceMessageRole("user")
                .sourceMessageHash("a".repeat(64))
                .metadata(Map.of("turn_index", 2))
                .createdAt("2026-07-21 10:00:00")
                .build();

        ColdMemoryItemSnapshot snapshot = mapper.snapshot(item);
        OpsMemoryItem view = mapper.views(List.of(snapshot)).get(0);

        assertEquals("fact", snapshot.memoryType());
        assertEquals(BigDecimal.valueOf(0.8), view.getImportance());
        assertEquals("a".repeat(64), view.getSourceMessageHash());
        assertEquals(2, view.getMetadata().get("turn_index"));
    }

    @Test
    void messageListRoundTripPreservesCompressionFields() {
        OpsMemoryMessage first = OpsMemoryMessage.builder()
                .sessionId("session-1")
                .userId("user-1")
                .role("system")
                .content("summary")
                .createdAt("2026-07-21 11:00:00")
                .metadata(Map.of("memory_type", "summary"))
                .build();

        List<ColdMemoryMessageSnapshot> snapshots = mapper.messageSnapshots(List.of(first));
        OpsMemoryMessage view = mapper.messageViews(snapshots).get(0);

        assertEquals("system", view.getRole());
        assertEquals("summary", view.getContent());
        assertEquals("summary", view.getMetadata().get("memory_type"));
        assertEquals("2026-07-21 11:00:00", view.getCreatedAt());
    }

    @Test
    void domainCandidateMapsToHistoricalTriggerItem() {
        MemoryItemCandidate candidate = new MemoryItemCandidate(
                "session-1",
                "user-1",
                "USER_PREFERENCE",
                "优先给结论",
                BigDecimal.valueOf(0.72D),
                "[\"user\"]",
                "user",
                "source-hash",
                Map.of("scopeType", "USER"),
                "2026-07-21 10:00:00");

        OpsMemoryItem view = mapper.candidateViews(List.of(candidate)).get(0);

        assertEquals("USER_PREFERENCE", view.getMemoryType());
        assertEquals("优先给结论", view.getContent());
        assertEquals("USER", view.getMetadata().get("scopeType"));
        assertEquals("source-hash", view.getSourceMessageHash());
    }
}
