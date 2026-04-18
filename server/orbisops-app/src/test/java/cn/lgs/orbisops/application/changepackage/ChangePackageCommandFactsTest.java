package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageApprovalActorContext;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackageCommandFactsTest {

    @Test
    void approvalContextPublishesOnlyStableDomainActorFacts() {
        ChangePackageApprovalContext context = ChangePackageApprovalContext.from(Map.of(
                "actorScope", "ADMIN",
                "adminConfirmation", true,
                "comment", "reviewed",
                "actorUsername", "alice",
                "actorUserId", "user-1",
                "serviceToken", "must-not-cross-domain"));

        ChangePackageApprovalActorContext actorContext = context.actorContext();

        assertEquals("admin", actorContext.actorScope());
        assertTrue(actorContext.adminConfirmation());
        assertEquals("reviewed", context.comment());
    }

    @Test
    void validationReportSeparatesExecutionTokenFromProofAndEventPayload() {
        ChangePackageValidationReport report = ChangePackageValidationReport.from(Map.of(
                "reasonCode", "READY_FOR_REVIEW",
                "_validationExecutionToken", "signed-token",
                "testProofHash", "proof-hash"));

        assertEquals("READY_FOR_REVIEW", report.effectiveReasonCode(true));
        assertEquals("signed-token", report.executionToken());
        assertEquals("proof-hash", report.proofPayload().get("testProofHash"));
        assertFalse(report.proofPayload().containsKey("reasonCode"));
        assertFalse(report.proofPayload().containsKey("_validationExecutionToken"));
        assertEquals("READY_FOR_REVIEW", report.eventPayload(true).get("reasonCode"));
        assertFalse(report.eventPayload(true).containsKey("_validationExecutionToken"));
    }

    @Test
    void mapCompatibilityConstructorsImmediatelyProduceNamedFacts() {
        ChangePackageCommands.SubmitReview submitReview = new ChangePackageCommands.SubmitReview(
                "cp-1", Map.of("comment", "submit"), "actor");
        ChangePackageCommands.Reject reject = new ChangePackageCommands.Reject(
                "cp-1", Map.of("reason", "unsafe", "comment", "reject"), "actor");
        ChangePackageCommands.Cleanup cleanup = new ChangePackageCommands.Cleanup(
                "cp-1", Map.of("repairWorkspaceId", "repair-1"), "actor");

        assertEquals("submit", submitReview.request().comment());
        assertEquals("unsafe", reject.request().reason());
        assertEquals("reject", reject.request().comment());
        assertEquals("repair-1", cleanup.request().repairWorkspaceId());
    }

    @Test
    void validationReportDefensivelyCopiesOpenProofFacts() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("reasonCode", "DRY_RUN_FAILED");
        source.put("detail", "original");

        ChangePackageValidationReport report = ChangePackageValidationReport.from(source);
        source.put("detail", "mutated");

        assertEquals("original", report.proofPayload().get("detail"));
        assertEquals("DRY_RUN_FAILED", report.eventPayload(false).get("reasonCode"));
    }
}
