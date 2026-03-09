package cn.lgs.orbisops.domain.toolset;

import cn.lgs.orbisops.domain.toolset.model.ApprovalRequirement;
import cn.lgs.orbisops.domain.toolset.model.CompensationCapability;
import cn.lgs.orbisops.domain.toolset.model.IdempotencyCapability;
import cn.lgs.orbisops.domain.toolset.model.ReconciliationCapability;
import cn.lgs.orbisops.domain.toolset.model.ToolEffect;
import cn.lgs.orbisops.domain.toolset.model.business.UpdateAlertThresholdCommand;
import cn.lgs.orbisops.domain.toolset.model.business.UpdateAlertThresholdPolicy;
import cn.lgs.orbisops.domain.toolset.model.business.UpdateAlertThresholdReceipt;
import cn.lgs.orbisops.domain.toolset.model.business.UpdateAlertThresholdReceiptHashing;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UpdateAlertThresholdContractTest {

    private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");

    @Test
    void trustedGovernanceIsSideEffectingApprovedIdempotentReconcilableAndRestorable() {
        var governance = UpdateAlertThresholdPolicy.governance();

        assertEquals(ToolEffect.SIDE_EFFECTING, governance.effect());
        assertEquals(ApprovalRequirement.OPERATOR_APPROVAL, governance.approvalRequirement());
        assertEquals(IdempotencyCapability.SERVER_RECEIPT, governance.idempotencyCapability());
        assertEquals(ReconciliationCapability.QUERY_BY_EXECUTION_KEY, governance.reconciliationCapability());
        assertEquals(CompensationCapability.STATE_RESTORE, governance.compensationCapability());
    }

    @Test
    void validCommandAndReceiptRequireExactCasTransition() {
        UpdateAlertThresholdCommand command = command();
        command.validateDeadline(Clock.fixed(NOW, ZoneOffset.UTC));
        UpdateAlertThresholdReceipt receipt = UpdateAlertThresholdReceipt.from(receipt(13));

        receipt.verify(command, UpdateAlertThresholdPolicy.TOOL_NAME);
        assertEquals("receipt-1", receipt.receiptId());
    }

    @Test
    void missingApprovalExpiredDeadlineAndReceiptVersionDriftFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> UpdateAlertThresholdCommand.from(
                Map.of(
                        "metric", "latency_p95",
                        "expectedValue", 800,
                        "expectedVersion", 12,
                        "newValue", 1000),
                "project-1", "execution-1", NOW.plusSeconds(30), "operator-1"));

        UpdateAlertThresholdCommand command = command();
        assertEquals("UPDATE_ALERT_THRESHOLD_DEADLINE_EXPIRED",
                assertThrows(IllegalArgumentException.class,
                        () -> command.validateDeadline(Clock.fixed(NOW.plusSeconds(61), ZoneOffset.UTC)))
                        .getMessage());

        UpdateAlertThresholdReceipt drifted = UpdateAlertThresholdReceipt.from(receipt(14));
        assertEquals("UPDATE_ALERT_THRESHOLD_RECEIPT_VERSION_MISMATCH",
                assertThrows(IllegalArgumentException.class, () -> drifted.verify(command)).getMessage());
    }

    @Test
    void duplicatedContextFieldsMustMatchInsteadOfBeingSilentlyOverridden() {
        Map<String, Object> exact = new LinkedHashMap<>(Map.ofEntries(
                Map.entry("projectId", "project-1"),
                Map.entry("metric", "latency_p95"),
                Map.entry("expectedValue", 800),
                Map.entry("expectedVersion", 12),
                Map.entry("newValue", 1000),
                Map.entry("approvalId", "approval-1"),
                Map.entry("executionKey", "execution-1"),
                Map.entry("deadline", NOW.plusSeconds(60).toString()),
                Map.entry("actor", "operator-1")));
        UpdateAlertThresholdCommand.from(
                exact,
                "project-1",
                "execution-1",
                NOW.plusSeconds(60),
                "operator-1");

        Map<String, Object> projectMismatch = new LinkedHashMap<>(exact);
        projectMismatch.put("projectId", "project-2");
        assertEquals(
                "UPDATE_ALERT_THRESHOLD_PROJECT_CONTEXT_MISMATCH",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> UpdateAlertThresholdCommand.from(
                                projectMismatch,
                                "project-1",
                                "execution-1",
                                NOW.plusSeconds(60),
                                "operator-1"))
                        .getMessage());

        Map<String, Object> executionMismatch = new LinkedHashMap<>(exact);
        executionMismatch.put("executionKey", "execution-2");
        assertEquals(
                "UPDATE_ALERT_THRESHOLD_EXECUTION_KEY_CONTEXT_MISMATCH",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> UpdateAlertThresholdCommand.from(
                                executionMismatch,
                                "project-1",
                                "execution-1",
                                NOW.plusSeconds(60),
                                "operator-1"))
                        .getMessage());

        Map<String, Object> deadlineMismatch = new LinkedHashMap<>(exact);
        deadlineMismatch.put("deadline", NOW.plusSeconds(120).toString());
        assertEquals(
                "UPDATE_ALERT_THRESHOLD_DEADLINE_CONTEXT_MISMATCH",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> UpdateAlertThresholdCommand.from(
                                deadlineMismatch,
                                "project-1",
                                "execution-1",
                                NOW.plusSeconds(60),
                                "operator-1"))
                        .getMessage());

        Map<String, Object> actorMismatch = new LinkedHashMap<>(exact);
        actorMismatch.put("actor", "operator-2");
        assertEquals(
                "UPDATE_ALERT_THRESHOLD_ACTOR_CONTEXT_MISMATCH",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> UpdateAlertThresholdCommand.from(
                                actorMismatch,
                                "project-1",
                                "execution-1",
                                NOW.plusSeconds(60),
                                "operator-1"))
                        .getMessage());
    }

    @Test
    void forgedOperationInputAndResultHashesFailClosed() {
        UpdateAlertThresholdCommand command = command();

        Map<String, Object> forgedInput = new LinkedHashMap<>(receipt(13));
        forgedInput.put("operationInputHash", "0".repeat(64));
        assertEquals(
                "UPDATE_ALERT_THRESHOLD_RECEIPT_INPUT_HASH_MISMATCH",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> UpdateAlertThresholdReceipt.from(forgedInput).verify(
                                command,
                                UpdateAlertThresholdPolicy.TOOL_NAME))
                        .getMessage());

        Map<String, Object> forgedResult = new LinkedHashMap<>(receipt(13));
        forgedResult.put("resultHash", "sha256:" + "0".repeat(64));
        assertEquals(
                "UPDATE_ALERT_THRESHOLD_RECEIPT_RESULT_HASH_MISMATCH",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> UpdateAlertThresholdReceipt.from(forgedResult).verify(
                                command,
                                UpdateAlertThresholdPolicy.TOOL_NAME))
                        .getMessage());
    }

    private Map<String, Object> receipt(int currentVersion) {
        UpdateAlertThresholdCommand command = command();
        Map<String, Object> payload = new LinkedHashMap<>(Map.ofEntries(
                Map.entry("status", "SUCCEEDED"),
                Map.entry("receiptId", "receipt-1"),
                Map.entry("operation", UpdateAlertThresholdPolicy.TOOL_NAME),
                Map.entry("executionKey", "execution-1"),
                Map.entry("projectId", "project-1"),
                Map.entry("metric", "latency_p95"),
                Map.entry("approvalId", "approval-1"),
                Map.entry("actor", "operator-1"),
                Map.entry("expectedVersion", 12),
                Map.entry("hashVersion", UpdateAlertThresholdReceiptHashing.HASH_VERSION),
                Map.entry("operationInputHash", UpdateAlertThresholdReceiptHashing.operationInputHash(
                        command,
                        UpdateAlertThresholdPolicy.TOOL_NAME)),
                Map.entry("previousValue", 800),
                Map.entry("currentValue", 1000),
                Map.entry("currentVersion", currentVersion),
                Map.entry("resultHash", "sha256:" + "0".repeat(64)),
                Map.entry("completedAt", "2026-08-03T12:00:02Z")));
        UpdateAlertThresholdReceipt provisional = UpdateAlertThresholdReceipt.from(payload);
        payload.put("resultHash", UpdateAlertThresholdReceiptHashing.resultHash(provisional));
        return Map.copyOf(payload);
    }

    private UpdateAlertThresholdCommand command() {
        return new UpdateAlertThresholdCommand(
                "project-1",
                "latency_p95",
                new BigDecimal("800"),
                12,
                new BigDecimal("1000"),
                "approval-1",
                "execution-1",
                NOW.plusSeconds(60),
                "operator-1");
    }
}
