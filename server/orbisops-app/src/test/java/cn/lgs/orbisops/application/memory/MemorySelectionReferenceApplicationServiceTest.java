package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.service.MemoryContentHashPolicy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MemorySelectionReferenceApplicationServiceTest {

    private static final Instant SELECTED_AT = Instant.parse("2026-07-21T10:45:00Z");

    private final MemorySelectionReferenceApplicationService service =
            new MemorySelectionReferenceApplicationService(
                    new MemoryContentHashPolicy(),
                    Clock.fixed(SELECTED_AT, ZoneOffset.UTC));

    @Test
    void assemblesTypedReferenceWithLegacyHashAndFixedClock() {
        ContextMemoryView memory = new ContextMemoryView(
                "ctx-1",
                "PROJECT_CONTEXT",
                "PROJECT",
                "demo-project",
                "DDD migration",
                "summary",
                "selected content",
                "source-hash",
                Map.of());

        List<MemorySelectionReference> references = service.assemble(List.of(memory));

        assertEquals(1, references.size());
        MemorySelectionReference reference = references.get(0);
        String expectedHash = new MemoryContentHashPolicy().stableHash("selected content");
        assertEquals("ctx-1", reference.memoryId());
        assertEquals(1, reference.version());
        assertEquals(expectedHash, reference.memoryHash());
        assertEquals(expectedHash, reference.contentHash());
        assertEquals("PROJECT_CONTEXT", reference.memoryType());
        assertEquals("PROJECT", reference.scope());
        assertEquals("demo-project", reference.scopeId());
        assertEquals("source-hash", reference.sourceMessageHash());
        assertEquals(SELECTED_AT.toString(), reference.selectedAt());
        assertFalse(reference.verified());
    }

    @Test
    void fallsBackToSummaryWhenContentIsBlank() {
        ContextMemoryView memory = new ContextMemoryView(
                null,
                null,
                null,
                null,
                null,
                "summary fallback",
                " ",
                null,
                Map.of());

        MemorySelectionReference reference = service.assemble(List.of(memory)).get(0);

        String expectedHash = new MemoryContentHashPolicy().stableHash("summary fallback");
        assertEquals("", reference.memoryId());
        assertEquals(expectedHash, reference.contentHash());
        assertEquals("", reference.memoryType());
        assertEquals("", reference.scope());
        assertEquals("", reference.scopeId());
    }

    @Test
    void ignoresNullEntriesAndReturnsImmutableList() {
        List<MemorySelectionReference> references = service.assemble(java.util.Arrays.asList(
                null,
                new ContextMemoryView("ctx-1", "TYPE", "SCOPE", "id", "", "", "", "", Map.of())));

        assertEquals(1, references.size());
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> references.add(references.get(0)));
        assertEquals(List.of(), service.assemble(null));
        assertEquals(List.of(), service.assemble(List.of()));
    }
}
