package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.application.changepackage.ChangePackageLandingProcessManager;
import cn.lgs.orbisops.application.changepackage.LandingOperationJournalApplicationService;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionApplicationService;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionIdempotencyPort;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpServerConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsProgressiveMcpInvocationService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsProjectMcpRuntimeConfigService;
import com.alibaba.fastjson.JSON;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reconciles quarantined production side effects by querying an authoritative provider receipt.
 * This worker is deliberately protocol-specific: it may query restart_service receipts, but it
 * never redispatches restart_service or any other production mutation.
 */
@Service
public class OpsToolSideEffectRecoveryService {

    private static final String RECEIPT_TOOL = "get_operation_receipt";
    private static final String RESTART_TOOL = "restart_service";
    private static final int SERVICE_CONTROL_HASH_VERSION = 1;
    private static final Set<String> REQUIRED_CONTEXT = Set.of(
            "adapterType",
            "mcpServerId",
            "remoteToolName",
            "actor",
            "executionScope",
            "changePackageId",
            "approvedPackageHash",
            "approvedPackageVersion",
            "operationId");

    private final ToolExecutionApplicationService toolExecution;
    private final OpsProjectMcpRuntimeConfigService runtimeConfigs;
    private final OpsProgressiveMcpInvocationService mcp;
    private final LandingOperationJournalApplicationService landingJournal;
    private final ChangePackageLandingProcessManager landingProcessManager;

    public OpsToolSideEffectRecoveryService(
            ToolExecutionApplicationService toolExecution,
            OpsProjectMcpRuntimeConfigService runtimeConfigs,
            OpsProgressiveMcpInvocationService mcp,
            LandingOperationJournalApplicationService landingJournal,
            ChangePackageLandingProcessManager landingProcessManager) {
        this.toolExecution = required(toolExecution, "TOOL_EXECUTION_SERVICE_REQUIRED");
        this.runtimeConfigs = required(runtimeConfigs, "PROJECT_MCP_RUNTIME_CONFIG_SERVICE_REQUIRED");
        this.mcp = required(mcp, "MCP_INVOCATION_SERVICE_REQUIRED");
        this.landingJournal = required(landingJournal, "LANDING_OPERATION_JOURNAL_SERVICE_REQUIRED");
        this.landingProcessManager = required(landingProcessManager, "CHANGE_PACKAGE_LANDING_PROCESS_MANAGER_REQUIRED");
    }

    @Scheduled(fixedDelayString = "${orbisops.tool-execution.side-effect-recovery.fixed-delay-ms:30000}")
    public void recoverScheduled() {
        recoverOnce(25);
    }

    public Map<String, Object> recoverOnce(int limit) {
        int scanned = 0;
        int reconciled = 0;
        int unresolved = 0;
        int unsupported = 0;
        for (ToolExecutionIdempotencyPort.UnresolvedSideEffect sideEffect
                : toolExecution.unresolvedSideEffects(Math.max(1, Math.min(limit, 100)))) {
            scanned++;
            if (!supported(sideEffect)) {
                unsupported++;
                continue;
            }
            try {
                Map<String, Object> receipt = queryReceipt(sideEffect);
                if (!trustedReceipt(sideEffect, receipt)) {
                    unresolved++;
                    continue;
                }
                complete(sideEffect, receipt);
                reconciled++;
            } catch (RuntimeException error) {
                // Policy unavailable, MCP unavailable, NOT_FOUND and malformed receipts all remain REVIEW_REQUIRED.
                unresolved++;
            }
        }
        return Map.of(
                "scanned", scanned,
                "reconciled", reconciled,
                "unresolved", unresolved,
                "unsupported", unsupported);
    }

    private boolean supported(ToolExecutionIdempotencyPort.UnresolvedSideEffect sideEffect) {
        if (sideEffect == null) return false;
        Map<String, Object> context = sideEffect.reconciliationContext();
        if (context == null || REQUIRED_CONTEXT.stream().anyMatch(key -> text(context.get(key)).isBlank())) {
            return false;
        }
        return "MCP".equalsIgnoreCase(text(context.get("adapterType")))
                && "APPROVED_LANDING".equalsIgnoreCase(text(context.get("executionScope")))
                && RESTART_TOOL.equals(text(context.get("remoteToolName")))
                && positiveInt(context.get("approvedPackageVersion")) > 0;
    }

    private Map<String, Object> queryReceipt(ToolExecutionIdempotencyPort.UnresolvedSideEffect sideEffect) {
        Map<String, Object> context = sideEffect.reconciliationContext();
        String mcpId = text(context.get("mcpServerId"));
        OpsMcpServerConfig config = runtimeConfigs.resolve(sideEffect.projectId(), mcpId)
                .orElseThrow(() -> new IllegalStateException("TOOL_SIDE_EFFECT_RECONCILIATION_MCP_UNAVAILABLE"));
        bindReconciliationAuthority(config, sideEffect, context, mcpId);
        String response = mcp.invoke(config, JSON.toJSONString(Map.of(
                "toolName", RECEIPT_TOOL,
                "arguments", Map.of(
                        "projectId", sideEffect.projectId(),
                        "executionKey", sideEffect.idempotencyKey(),
                        "actor", text(context.get("actor"))))));
        return responseObject(response);
    }

    private void bindReconciliationAuthority(
            OpsMcpServerConfig config,
            ToolExecutionIdempotencyPort.UnresolvedSideEffect sideEffect,
            Map<String, Object> context,
            String mcpId) {
        config.setProjectId(sideEffect.projectId());
        config.setRunId(sideEffect.runId());
        config.setAgentId("platform-landing-react");
        config.setNodeId("tool-side-effect-reconciliation");
        config.setMcpId(mcpId);
        config.setToolId(mcpId);
        config.setOperationId(text(context.get("operationId")));
        config.setToolCallStage("LANDING");
        config.setAuthorityDeadline(Instant.now().plusSeconds(30));
        config.setLandingApproved(true);
        config.setChangePackageId(text(context.get("changePackageId")));
        config.setApprovedPackageHash(text(context.get("approvedPackageHash")));
        config.setApprovedPackageVersion(positiveInt(context.get("approvedPackageVersion")));
        config.setAllowedTools(List.of(RECEIPT_TOOL));
        config.setBlockedTools(List.of());
        Map<String, String> capabilities = new LinkedHashMap<>(
                config.getToolCapabilities() == null ? Map.of() : config.getToolCapabilities());
        capabilities.put(RECEIPT_TOOL, "READ_ONLY");
        config.setToolCapabilities(capabilities);
    }

    private boolean trustedReceipt(
            ToolExecutionIdempotencyPort.UnresolvedSideEffect sideEffect,
            Map<String, Object> receipt) {
        if (receipt == null || receipt.isEmpty()) return false;
        Map<String, Object> context = sideEffect.reconciliationContext();
        if (!"SUCCEEDED".equalsIgnoreCase(text(receipt.get("status")))) return false;
        if (!RESTART_TOOL.equals(text(receipt.get("operation")))) return false;
        if (!sideEffect.idempotencyKey().equals(text(receipt.get("executionKey")))) return false;
        if (!sideEffect.projectId().equals(text(receipt.get("projectId")))) return false;
        if (!text(context.get("actor")).equals(text(receipt.get("actor")))) return false;
        if (text(receipt.get("receiptId")).isBlank()) return false;
        if (positiveInt(receipt.get("hashVersion")) != SERVICE_CONTROL_HASH_VERSION) return false;
        String resultHash = text(receipt.get("resultHash"));
        if (!resultHash.matches("sha256:[0-9a-f]{64}")) return false;
        return resultHash.substring("sha256:".length()).equals(CanonicalObjectHasher.sha256(receiptHashFacts(receipt)));
    }

    private Map<String, Object> receiptHashFacts(Map<String, Object> receipt) {
        Map<String, Object> facts = new LinkedHashMap<>();
        for (String key : List.of(
                "status", "receiptId", "operation", "executionKey", "projectId", "service", "actor",
                "expectedVersion", "previousVersion", "currentVersion", "previousRestartCount",
                "currentRestartCount", "completedAt", "operationInputHash", "hashVersion")) {
            facts.put(key, receipt.get(key));
        }
        return facts;
    }

    private void complete(
            ToolExecutionIdempotencyPort.UnresolvedSideEffect sideEffect,
            Map<String, Object> receipt) {
        Map<String, Object> context = sideEffect.reconciliationContext();
        String receiptId = text(receipt.get("receiptId"));
        String evidenceHash = text(receipt.get("resultHash")).substring("sha256:".length());
        Map<String, Object> result = new LinkedHashMap<>(receipt);
        result.put("resultId", receiptId);
        result.put("remoteRequestId", receiptId);
        result.put("outputHash", evidenceHash);
        result.put("authoritative", true);

        landingJournal.completeFromToolExecution(
                sideEffect.runId(),
                text(context.get("operationId")),
                RESTART_TOOL,
                sideEffect.idempotencyKey(),
                landingJournal.payload(result));
        toolExecution.resolveUncertainSideEffect(new ToolExecutionIdempotencyPort.ResolveSideEffectCommand(
                sideEffect.idempotencyKey(),
                sideEffect.projectId(),
                sideEffect.runId(),
                ToolExecutionIdempotencyPort.SideEffectResolution.CONFIRMED_SUCCEEDED,
                receiptId,
                evidenceHash,
                "Authoritative service-control operation receipt verified; production write was not redispatched.",
                "tool-side-effect-recovery",
                Instant.now()));
        landingProcessManager.reconcile(sideEffect.runId(), "tool-side-effect-recovery");
    }

    private Map<String, Object> responseObject(String raw) {
        if (!StringUtils.hasText(raw)) return Map.of();
        Object parsed;
        try {
            parsed = JSON.parse(raw);
        } catch (RuntimeException error) {
            return Map.of();
        }
        return responseObject(parsed, 0);
    }

    private Map<String, Object> responseObject(Object value, int depth) {
        if (depth > 4 || value == null) return Map.of();
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> map = new LinkedHashMap<>();
            source.forEach((key, item) -> map.put(String.valueOf(key), item));
            if (map.containsKey("status") && map.containsKey("executionKey")) return map;
            Object result = map.get("result");
            Map<String, Object> nested = responseObject(result, depth + 1);
            if (!nested.isEmpty()) return nested;
            Object content = map.get("content");
            if (content instanceof Iterable<?> iterable) {
                for (Object item : iterable) {
                    nested = responseObject(item, depth + 1);
                    if (!nested.isEmpty()) return nested;
                }
            }
            Object text = map.get("text");
            if (text instanceof String string && StringUtils.hasText(string)) {
                try {
                    return responseObject(JSON.parse(string), depth + 1);
                } catch (RuntimeException ignored) {
                    return Map.of();
                }
            }
            return Map.of();
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                Map<String, Object> nested = responseObject(item, depth + 1);
                if (!nested.isEmpty()) return nested;
            }
        }
        if (value instanceof String string && StringUtils.hasText(string)) {
            try {
                return responseObject(JSON.parse(string), depth + 1);
            } catch (RuntimeException ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private int positiveInt(Object value) {
        if (value instanceof Number number) return Math.max(0, number.intValue());
        try {
            return Math.max(0, Integer.parseInt(text(value)));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private <T> T required(T value, String code) {
        if (value == null) throw new IllegalArgumentException(code);
        return value;
    }
}
