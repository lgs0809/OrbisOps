package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.support.EvidenceTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsToolResultStoreTest {

    @Test
    void compatibilityAclRecordsReadsGrepsSlicesAndListsTypedResults() {
        OpsToolResultStore store = EvidenceTestSupport.toolResultStore();
        Map<String, Object> stored = store.record(
                "project-1", "session-1", "run-1", "user-1",
                "code.repair", "code_bash", "CONTROLLED_BASH_EXECUTED", "npm test",
                "line1\nDB_PASSWORD=secret\nline3 keyword\nline4",
                OpsToolOutputBudget.builder().maxBytes(12).build(), "user-1");

        assertTrue((Boolean) stored.get("truncated"));
        assertTrue(String.valueOf(stored.get("resultId")).startsWith("tool-result-test-"));
        assertFalse(stored.containsKey("fullOutput"));
        assertTrue(String.valueOf(stored.get("preview")).contains("line1"));

        String resultId = String.valueOf(stored.get("resultId"));
        Map<String, Object> read = store.read(resultId, "project-1", "user-1", 0, 10);
        assertTrue(String.valueOf(read.get("lines")).contains("DB_PASSWORD=***"));
        assertEquals(1, store.grep(resultId, "project-1", "user-1", "keyword").get("count"));
        assertEquals(2, ((List<?>) store.slice(resultId, "project-1", "user-1", 2, 3).get("lines")).size());
        assertEquals(1, store.listForRun("project-1", "run-1", 10).size());

        assertThrows(SecurityException.class,
                () -> store.read(resultId, "other-project", "user-1", 0, 1));
    }

    @Test
    void readinessReflectsExplicitTestMemoryRepository() {
        Map<String, Object> readiness = EvidenceTestSupport.toolResultStore().readiness();

        assertEquals("DEGRADED_MEMORY", readiness.get("status"));
        assertEquals(true, readiness.get("memoryFallbackAllowed"));
    }

    @Test
    void unicodePreviewNeverSplitsCodePoint() {
        OpsToolResultStore store = EvidenceTestSupport.toolResultStore();
        Map<String, Object> stored = store.record(
                "project-1", "", "run-1", "user-1", "toolset", "tool", "TEST",
                "query", "中中中", OpsToolOutputBudget.builder().maxBytes(4).build(), "user-1");

        assertEquals("中", stored.get("preview"));
    }
}
