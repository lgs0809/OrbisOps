package cn.lgs.orbisops.domain.changepackage.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChangePackageAggregateTest {

    @Test
    void revisionCreatesNextUnapprovedPointer() {
        ChangePackageAggregate aggregate = ChangePackageAggregate.rehydrate(pointer(
                ChangePackageStatus.VALIDATION_FAILED, 2, "hash-2", 0, ""));

        ChangePackagePointer revised = aggregate.revise(3, "hash-3");

        assertEquals(ChangePackageStatus.REVISING, revised.status());
        assertEquals(3, revised.version());
        assertEquals("hash-3", revised.packageHash());
        assertEquals(0, revised.approvedVersion());
    }

    @Test
    void approvalRequiresCurrentVersionAndHash() {
        ChangePackageAggregate aggregate = ChangePackageAggregate.rehydrate(pointer(
                ChangePackageStatus.REVIEWING, 4, "hash-4", 0, ""));

        assertThrows(IllegalStateException.class, () -> aggregate.approve(3, "hash-4"));
        assertThrows(IllegalStateException.class, () -> aggregate.approve(4, "other-hash"));

        ChangePackagePointer approved = aggregate.approve(4, "hash-4");
        assertEquals(ChangePackageStatus.APPROVED, approved.status());
        assertEquals(4, approved.approvedVersion());
        assertEquals("hash-4", approved.approvedPackageHash());
    }

    @Test
    void landingPreservesFrozenApprovalPointer() {
        ChangePackageAggregate aggregate = ChangePackageAggregate.rehydrate(pointer(
                ChangePackageStatus.APPROVED, 5, "hash-5", 5, "hash-5"));

        aggregate.startLanding();
        ChangePackagePointer landed = aggregate.finishLanding(ChangePackageStatus.LANDED);

        assertEquals(ChangePackageStatus.LANDED, landed.status());
        assertEquals(5, landed.approvedVersion());
        assertEquals("hash-5", landed.approvedPackageHash());
    }

    @Test
    void landingOutcomeCannotBypassRunningState() {
        ChangePackageAggregate aggregate = ChangePackageAggregate.rehydrate(pointer(
                ChangePackageStatus.APPROVED, 5, "hash-5", 5, "hash-5"));

        assertThrows(IllegalStateException.class,
                () -> aggregate.finishLanding(ChangePackageStatus.LANDED));
    }

    private ChangePackagePointer pointer(ChangePackageStatus status,
                                         int version,
                                         String packageHash,
                                         int approvedVersion,
                                         String approvedPackageHash) {
        return new ChangePackagePointer("cp-1", status, version, packageHash,
                approvedVersion, approvedPackageHash);
    }
}
