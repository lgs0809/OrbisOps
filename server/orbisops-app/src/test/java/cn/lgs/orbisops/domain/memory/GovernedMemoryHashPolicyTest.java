package cn.lgs.orbisops.domain.memory;

import cn.lgs.orbisops.domain.memory.model.GovernedMemoryDraft;
import cn.lgs.orbisops.domain.memory.service.GovernedMemoryHashPolicy;
import cn.lgs.orbisops.domain.memory.service.GovernedMemoryPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernedMemoryHashPolicyTest {

    private final GovernedMemoryPolicy memoryPolicy = new GovernedMemoryPolicy();
    private final GovernedMemoryHashPolicy hashPolicy = new GovernedMemoryHashPolicy();

    @Test
    void idempotencySeparatesLogicalKeysAndRunIdentity() {
        GovernedMemoryDraft draft = memoryPolicy.draft(
                "PROJECT",
                "demo-project",
                "PROJECT_FACT",
                "MySQL 主库",
                "MySQL 主库",
                "mysql-primary",
                "USER_ASSERTED",
                false,
                0.6D,
                "LOW");

        assertEquals(
                "a4d166b8a3d530e5babe30129aab9b4e4d23dc3351ea602a5199745c8d18625e",
                hashPolicy.idempotencyKey(draft, "run-1"));
    }

    @Test
    void hashesCanonicalSnapshotBytesWithoutOwningJsonCodec() {
        assertEquals(
                "5041bf1f713df204784353e82f6a4a535931cb64f1f4b4a5aeaffcb720918b22",
                hashPolicy.memoryHash("{\"x\":1}"));
        assertThrows(IllegalArgumentException.class,
                () -> hashPolicy.memoryHash(" "));
    }

    @Test
    void conflictComparisonIsNullSafeAndExact() {
        assertFalse(hashPolicy.conflicts("abc", "abc"));
        assertTrue(hashPolicy.conflicts("abc", "def"));
        assertFalse(hashPolicy.conflicts(null, null));
        assertTrue(hashPolicy.conflicts(null, "abc"));
    }

    @Test
    void rejectsMissingDraft() {
        assertThrows(IllegalArgumentException.class,
                () -> hashPolicy.idempotencyKey(null, "run-1"));
    }
}
