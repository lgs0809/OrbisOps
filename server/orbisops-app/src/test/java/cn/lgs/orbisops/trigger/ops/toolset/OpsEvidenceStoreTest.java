package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.support.EvidenceTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsEvidenceStoreTest {

    private static final String OUTPUT_HASH = "a".repeat(64);

    @Test
    void compatibilityAclRecordsIdempotentlyRequiresScopeAndListsRunEvidence() {
        OpsEvidenceStore store = EvidenceTestSupport.evidenceStore();
        Map<String, Object> first = store.record(
                "project-1", "run-1", "TOOL", "code_bash", "tool-result-1",
                OUTPUT_HASH, "db:tool-result-1", "tests passed", true,
                Map.of("exitCode", 0), "alice");
        Map<String, Object> second = store.record(
                "project-1", "run-1", "TOOL", "code_bash", "tool-result-1",
                OUTPUT_HASH, "db:tool-result-1", "tests passed again", true,
                Map.of("exitCode", 0), "alice");

        assertEquals(first.get("evidenceId"), second.get("evidenceId"));
        assertEquals(OUTPUT_HASH, store.require(
                String.valueOf(first.get("evidenceId")), "project-1", "run-1").get("outputHash"));
        List<Map<String, Object>> listed = store.listForRun("project-1", "run-1", 10);
        assertEquals(1, listed.size());
        assertTrue((Boolean) listed.get(0).get("verified"));

        assertThrows(SecurityException.class, () -> store.require(
                String.valueOf(first.get("evidenceId")), "other-project", "run-1"));
    }

    @Test
    void rejectsIncompleteOrInvalidEvidence() {
        OpsEvidenceStore store = EvidenceTestSupport.evidenceStore();

        assertThrows(IllegalStateException.class, () -> store.record(
                "", "run-1", "TOOL", "", "tool-result-1", OUTPUT_HASH,
                "db:tool-result-1", "summary", true, Map.of(), "alice"));
        assertThrows(IllegalArgumentException.class, () -> store.record(
                "project-1", "run-1", "TOOL", "", "tool-result-1", "not-a-hash",
                "db:tool-result-1", "summary", true, Map.of(), "alice"));
    }
}
