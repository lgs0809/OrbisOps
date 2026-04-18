package cn.lgs.orbisops.domain.changepackage.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackagePointerTest {

    @Test
    void approvedLifecycleRequiresFrozenPointer() {
        assertThrows(IllegalArgumentException.class, () -> new ChangePackagePointer(
                "cp-1", ChangePackageStatus.APPROVED, 2, "hash-2", 0, ""));
    }

    @Test
    void preApprovalLifecycleRejectsStaleApprovalPointer() {
        assertThrows(IllegalArgumentException.class, () -> new ChangePackagePointer(
                "cp-1", ChangePackageStatus.REVIEWING, 2, "hash-2", 2, "hash-2"));
    }

    @Test
    void landingTransitionKeepsFrozenPointer() {
        ChangePackagePointer approved = new ChangePackagePointer(
                "cp-1", ChangePackageStatus.APPROVED, 2, "hash-2", 2, "hash-2");

        ChangePackagePointer running = approved.withStatus(ChangePackageStatus.LANDING_RUNNING);

        assertEquals(ChangePackageStatus.LANDING_RUNNING, running.status());
        assertTrue(running.approved());
    }
}
