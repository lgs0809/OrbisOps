package cn.lgs.orbisops.domain.changepackage;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.service.ChangePackageStatusPolicy;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChangePackageStatusPolicyTest {

    @Test
    void everyStatusHasStableProductQueue() {
        ChangePackageStatusPolicy policy = new ChangePackageStatusPolicy();
        Map<ChangePackageStatus, String> expected = Map.ofEntries(
                Map.entry(ChangePackageStatus.DRAFT, "PENDING"),
                Map.entry(ChangePackageStatus.VALIDATING, "PENDING"),
                Map.entry(ChangePackageStatus.READY_FOR_REVIEW, "PENDING"),
                Map.entry(ChangePackageStatus.REVIEWING, "PENDING"),
                Map.entry(ChangePackageStatus.REVISING, "PENDING"),
                Map.entry(ChangePackageStatus.APPROVED, "RUNNING"),
                Map.entry(ChangePackageStatus.LANDING_RUNNING, "RUNNING"),
                Map.entry(ChangePackageStatus.VALIDATION_FAILED, "INTERVENTION"),
                Map.entry(ChangePackageStatus.REJECTED, "INTERVENTION"),
                Map.entry(ChangePackageStatus.LANDING_FAILED, "INTERVENTION"),
                Map.entry(ChangePackageStatus.NEEDS_REPLAN, "INTERVENTION"),
                Map.entry(ChangePackageStatus.LANDED, "HISTORY"),
                Map.entry(ChangePackageStatus.CLOSED, "HISTORY"));

        for (ChangePackageStatus status : ChangePackageStatus.values()) {
            assertEquals(expected.get(status), policy.productQueue(status.name()), status.name());
        }
    }
}
