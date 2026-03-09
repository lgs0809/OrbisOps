package cn.lgs.orbisops.domain.memory.service;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;
import cn.lgs.orbisops.domain.memory.model.ContextMemorySnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextMemoryProjectionPolicyTest {

    private final ContextMemoryProjectionPolicy policy = new ContextMemoryProjectionPolicy();

    @Test
    void projectsProjectItemWithDefaultsAndStableIdentity() {
        ColdMemoryItemSnapshot item = new ColdMemoryItemSnapshot(
                "session-1",
                "user-1",
                "project_context",
                "DDD migration",
                BigDecimal.valueOf(1.5D),
                "[\"ddd\"]",
                "user",
                "source-hash",
                Map.of("projectId", "demo-project"),
                "2026-07-21 20:00:00");

        ContextMemorySnapshot projected = policy.project(item).orElseThrow();

        String expectedId = "ctx-mem-" + new MemoryContentHashPolicy().stableHash(
                "PROJECT:demo-project:PROJECT_CONTEXT:DDD migration");
        assertEquals(expectedId, projected.memoryId());
        assertEquals("PROJECT", projected.scopeType());
        assertEquals("demo-project", projected.scopeId());
        assertEquals("PROJECT_CONTEXT", projected.memoryType());
        assertEquals("PROJECT_CONTEXT", projected.title());
        assertEquals("DDD migration", projected.summary());
        assertEquals("ACTIVE", projected.status());
        assertEquals(BigDecimal.ONE, projected.confidence());
        assertEquals("memory_extractor", projected.sourceType());
        assertEquals("session-1", projected.sourceId());
        assertEquals("source-hash", projected.sourceMessageHash());
        assertEquals("user-1", projected.createdBy());
    }

    @Test
    void projectsUserItemUsingExplicitMetadataDefaults() {
        ColdMemoryItemSnapshot item = new ColdMemoryItemSnapshot(
                "session-1",
                "user-1",
                "USER_PREFERENCE",
                "Prefer concise conclusions",
                BigDecimal.valueOf(0.7D),
                "[]",
                "user",
                "source-hash",
                Map.of(
                        "scopeType", "user",
                        "title", "Response preference",
                        "summary", "Concise first",
                        "memory_status", "archived",
                        "source", "manual_extractor"),
                "");

        ContextMemorySnapshot projected = policy.project(item).orElseThrow();

        assertEquals("USER", projected.scopeType());
        assertEquals("user-1", projected.scopeId());
        assertEquals("Response preference", projected.title());
        assertEquals("Concise first", projected.summary());
        assertEquals("ARCHIVED", projected.status());
        assertEquals("manual_extractor", projected.sourceType());
    }

    @Test
    void skipsInvalidOrUnscopedExtractedItems() {
        assertTrue(policy.project(null).isEmpty());
        assertTrue(policy.project(item("", "PROJECT_CONTEXT", Map.of("projectId", "demo-project"))).isEmpty());
        assertTrue(policy.project(item("content", "UNKNOWN", Map.of("projectId", "demo-project"))).isEmpty());
        assertTrue(policy.project(item("content", "PROJECT_CONTEXT", Map.of())).isEmpty());
        assertTrue(policy.project(item("content", "USER_PREFERENCE", Map.of())).isEmpty());
    }

    @Test
    void abbreviatesLongSummaryFallback() {
        ContextMemorySnapshot projected = policy.project(item(
                "x".repeat(300),
                "PROJECT_CONTEXT",
                Map.of("projectId", "demo-project"))).orElseThrow();

        assertEquals(243, projected.summary().length());
        assertTrue(projected.summary().endsWith("..."));
    }

    private ColdMemoryItemSnapshot item(String content,
                                        String memoryType,
                                        Map<String, Object> metadata) {
        return new ColdMemoryItemSnapshot(
                "session-1",
                "",
                memoryType,
                content,
                BigDecimal.valueOf(0.8D),
                "[]",
                "user",
                "source-hash",
                metadata,
                "");
    }
}
