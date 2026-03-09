package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.domain.toolset.model.ToolProviderDescriptor;
import cn.lgs.orbisops.domain.toolset.model.ToolProviderType;
import cn.lgs.orbisops.domain.toolset.model.ToolRiskLevel;
import cn.lgs.orbisops.domain.toolset.model.ToolSchema;
import cn.lgs.orbisops.domain.toolset.model.ToolSemantics;
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

class OpsUpdateAlertThresholdInvocationContractTest {

    private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");

    @Test
    void preparesExactTypedServerRequestAndAcceptsMatchingReceipt() {
        OpsUpdateAlertThresholdInvocationContract contract =
                new OpsUpdateAlertThresholdInvocationContract(Clock.fixed(NOW, ZoneOffset.UTC));
        ToolExecutionRequest prepared = contract.prepare(target(), request());

        assertEquals("project-1", prepared.arguments().get("projectId"));
        assertEquals("execution-1", prepared.arguments().get("executionKey"));
        assertEquals("operator-1", prepared.arguments().get("actor"));
        assertEquals("2026-08-03T12:01:00Z", prepared.arguments().get("deadline"));

        Map<String, Object> providerReceipt = receipt(13);
        Object output = contract.validateOutput(target(), prepared, Map.of(
                "providerResult", providerReceipt));
        assertEquals("receipt-1", ((Map<?, ?>) output).get("receiptId"));
        assertEquals(providerReceipt.get("resultHash"), ((Map<?, ?>) output).get("resultHash"));
    }

    @Test
    void missingApprovalAndUntrustedReceiptAreRejectedBeforeAuthoritativeCompletion() {
        OpsUpdateAlertThresholdInvocationContract contract =
                new OpsUpdateAlertThresholdInvocationContract(Clock.fixed(NOW, ZoneOffset.UTC));
        ToolExecutionRequest missingApproval = new ToolExecutionRequest(
                "project-1", "operator-1", "operator-1",
                UpdateAlertThresholdPolicy.TOOLSET_ID,
                UpdateAlertThresholdPolicy.TOOL_NAME,
                ToolExecutionScope.APPROVED_LANDING,
                Map.of(
                        "metric", "latency_p95",
                        "expectedValue", 800,
                        "expectedVersion", 12,
                        "newValue", 1000),
                "session-1", "run-1",
                Map.of(
                        "idempotencyKey", "execution-1",
                        "deadline", "2026-08-03T12:01:00Z"),
                Map.of("landingApproved", true));

        assertEquals("UPDATE_ALERT_THRESHOLD_APPROVAL_REQUIRED",
                assertThrows(IllegalArgumentException.class,
                        () -> contract.prepare(target(), missingApproval)).getMessage());

        ToolExecutionRequest prepared = contract.prepare(target(), request());
        assertEquals("UPDATE_ALERT_THRESHOLD_RECEIPT_VERSION_MISMATCH",
                assertThrows(IllegalArgumentException.class,
                        () -> contract.validateOutput(target(), prepared, receipt(14))).getMessage());
    }

    private ToolExecutionTarget target() {
        ToolSemantics semantics = new ToolSemantics(
                false, false, true, true, true,
                ToolRiskLevel.HIGH, true, true);
        return new ToolExecutionTarget(
                UpdateAlertThresholdPolicy.TOOLSET_ID,
                UpdateAlertThresholdPolicy.TOOL_NAME,
                "MCP",
                ToolRiskLevel.HIGH.name(),
                false,
                false,
                true,
                true,
                true,
                new ToolProviderDescriptor(
                        ToolProviderType.MCP,
                        "ops-mcp",
                        "MCP",
                        "",
                        "ops-mcp",
                        UpdateAlertThresholdPolicy.TOOL_NAME,
                        "{}",
                        "{}"),
                semantics,
                new ToolSchema(
                        UpdateAlertThresholdPolicy.INPUT_SCHEMA,
                        UpdateAlertThresholdPolicy.OUTPUT_SCHEMA),
                UpdateAlertThresholdPolicy.governance());
    }

    private Map<String, Object> receipt(int currentVersion) {
        UpdateAlertThresholdCommand command = new UpdateAlertThresholdCommand(
                "project-1",
                "latency_p95",
                new BigDecimal("800"),
                12,
                new BigDecimal("1000"),
                "approval-1",
                "execution-1",
                Instant.parse("2026-08-03T12:01:00Z"),
                "operator-1");
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

    private ToolExecutionRequest request() {
        return new ToolExecutionRequest(
                "project-1", "operator-1", "operator-1",
                UpdateAlertThresholdPolicy.TOOLSET_ID,
                UpdateAlertThresholdPolicy.TOOL_NAME,
                ToolExecutionScope.APPROVED_LANDING,
                Map.of(
                        "metric", "latency_p95",
                        "expectedValue", 800,
                        "expectedVersion", 12,
                        "newValue", 1000,
                        "approvalId", "approval-1"),
                "session-1", "run-1",
                Map.of(
                        "idempotencyKey", "execution-1",
                        "deadline", "2026-08-03T12:01:00Z"),
                Map.of("landingApproved", true));
    }
}
