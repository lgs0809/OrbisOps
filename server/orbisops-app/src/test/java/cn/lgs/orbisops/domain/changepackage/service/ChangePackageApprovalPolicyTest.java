package cn.lgs.orbisops.domain.changepackage.service;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageApprovalReview;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChangePackageApprovalPolicyTest {

    private final ChangePackageApprovalPolicy policy = new ChangePackageApprovalPolicy();

    @Test
    void lowRiskRequiresOneDistinctApprover() {
        ChangePackageApprovalPolicy.ApprovalRequirement requirement = policy.evaluate(
                review("LOW", "requester", "approver", false, false, "", false));

        assertEquals(1, requirement.requiredApprovals());
    }

    @Test
    void mediumExecutablePackageRequiresValidation() {
        assertThrows(IllegalStateException.class, () -> policy.evaluate(
                review("MEDIUM", "requester", "approver", false, true, "VALIDATION_FAILED", true)));
    }

    @Test
    void highRiskRequiresTwoDistinctApprovers() {
        ChangePackageApprovalPolicy.ApprovalRequirement requirement = policy.evaluate(
                review("HIGH", "requester", "approver", false, true, "ACCEPTABLE", true));

        assertEquals(2, requirement.requiredApprovals());
    }

    @Test
    void creatorCannotApproveOwnPackageAtAnyRisk() {
        assertThrows(SecurityException.class, () -> policy.evaluate(
                review("LOW", "same-user", "same-user", false, false, "", false)));
    }

    @Test
    void criticalRequiresAdminConfirmationAndRollbackMaterial() {
        assertThrows(SecurityException.class, () -> policy.evaluate(
                review("CRITICAL", "requester", "approver", false, true, "ACCEPTABLE", true)));
        assertThrows(IllegalStateException.class, () -> policy.evaluate(
                review("CRITICAL", "requester", "approver", true, true, "ACCEPTABLE", false)));

        ChangePackageApprovalPolicy.ApprovalRequirement requirement = policy.evaluate(
                review("CRITICAL", "requester", "approver", true, true, "ACCEPTABLE", true));
        assertEquals(2, requirement.requiredApprovals());
    }

    private ChangePackageApprovalReview review(String risk,
                                               String creator,
                                               String approver,
                                               boolean admin,
                                               boolean validationRequired,
                                               String assessment,
                                               boolean rollback) {
        return new ChangePackageApprovalReview(risk, creator, approver, admin ? "admin" : "approver",
                admin, validationRequired, assessment, rollback);
    }
}
