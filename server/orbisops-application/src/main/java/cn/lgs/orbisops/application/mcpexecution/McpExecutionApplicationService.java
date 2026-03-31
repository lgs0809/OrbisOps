package cn.lgs.orbisops.application.mcpexecution;

import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionConfig;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionDecision;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionRecordedResult;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionRequest;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionResponse;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionTarget;
import cn.lgs.orbisops.domain.mcpexecution.service.McpExecutionPolicy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

public final class McpExecutionApplicationService {

    private final McpExecutionRuntimePort runtime;
    private final McpExecutionRemotePort remote;
    private final McpExecutionRouterPort router;
    private final McpExecutionRecordPort records;
    private final McpExecutionAuditPort audits;
    private final LongSupplier nanoTimeSupplier;
    private final McpExecutionPolicy policy;

    public McpExecutionApplicationService(
            McpExecutionRuntimePort runtime,
            McpExecutionRemotePort remote,
            McpExecutionRouterPort router,
            McpExecutionRecordPort records,
            McpExecutionAuditPort audits,
            LongSupplier nanoTimeSupplier) {
        this(runtime, remote, router, records, audits, nanoTimeSupplier, new McpExecutionPolicy());
    }

    McpExecutionApplicationService(
            McpExecutionRuntimePort runtime,
            McpExecutionRemotePort remote,
            McpExecutionRouterPort router,
            McpExecutionRecordPort records,
            McpExecutionAuditPort audits,
            LongSupplier nanoTimeSupplier,
            McpExecutionPolicy policy) {
        if (runtime == null) throw new IllegalArgumentException("MCP_EXECUTION_RUNTIME_PORT_REQUIRED");
        if (remote == null) throw new IllegalArgumentException("MCP_EXECUTION_REMOTE_PORT_REQUIRED");
        if (router == null) throw new IllegalArgumentException("MCP_EXECUTION_ROUTER_PORT_REQUIRED");
        if (records == null) throw new IllegalArgumentException("MCP_EXECUTION_RECORD_PORT_REQUIRED");
        if (audits == null) throw new IllegalArgumentException("MCP_EXECUTION_AUDIT_PORT_REQUIRED");
        if (nanoTimeSupplier == null) throw new IllegalArgumentException("MCP_EXECUTION_NANO_TIME_SUPPLIER_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("MCP_EXECUTION_POLICY_REQUIRED");
        this.runtime = runtime;
        this.remote = remote;
        this.router = router;
        this.records = records;
        this.audits = audits;
        this.nanoTimeSupplier = nanoTimeSupplier;
        this.policy = policy;
    }

    public McpExecutionResponse discover(McpExecutionRequest request) {
        requireRequest(request);
        McpRuntimeCatalog catalog = runtime.catalog(request.config());
        return recordResponse(
                request,
                "tool_catalog",
                "MCP_DISCOVERY",
                "SUCCEEDED",
                Map.of("status", "SUCCEEDED", "tools", catalog.tools(), "count", catalog.tools().size()),
                true,
                "ALLOWED",
                0L);
    }

    public McpExecutionResponse activate(McpExecutionRequest request) {
        requireRequest(request);
        String toolName = policy.requireToolName(request);
        McpExecutionDecision authorization = policy.authorize(request.config(), toolName);
        if (!authorization.allowed()) {
            throw denied(request, toolName, authorization, 0L);
        }
        try {
            Map<String, Object> definition = remote.inspect(request.config(), toolName);
            McpRuntimeToolSchema disclosed = runtime.hydrate(request, toolName, definition);
            McpExecutionDecision reviewed = policy.policyDecision(disclosed.attributes(), toolName);
            if (!reviewed.allowed()) {
                return blockedResponse(request, toolName, reviewed, 0L);
            }
            if (!request.trustedLandingRuntime()
                    && policy.target(request.config(), toolName, disclosed.attributes()).requiresChangePackage()) {
                // Reading a reviewed contract grants no runtime activation or execution authority.
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("status", "SCHEMA_ONLY");
                payload.put("toolName", toolName);
                payload.put("definition", definition);
                payload.put("activated", false);
                payload.put("executionAllowed", false);
                payload.put("requiresApprovedPackage", true);
                payload.put("nextStep", "使用此参数定义生成 ChangePackage；审批前不得调用该工具，审批后仅由 Landing 运行时执行。");
                return recordResponse(request, toolName, "MCP_TOOL_DISCLOSURE",
                        "SCHEMA_ONLY", payload, true, "ALLOWED", 0L);
            }
            McpRuntimeActivation activation = runtime.activate(
                    request, toolName, text(request.input().get("reason")), definition);
            return recordResponse(
                    request, toolName, "MCP_TOOL_DISCLOSURE",
                    activation.status(), activation.payload(),
                    true, "ALLOWED", 0L);
        } catch (SecurityException error) {
            String code = policy.activationReasonCode(error.getMessage());
            McpExecutionDecision denied = McpExecutionDecision.blocked(
                    code, message(error, "MCP 工具当前不可用"));
            return blockedResponse(request, toolName, denied, 0L);
        }
    }

    public McpExecutionResponse execute(McpExecutionRequest request) {
        requireRequest(request);
        long started = nanoTimeSupplier.getAsLong();
        String toolName = policy.requireToolName(request);
        McpExecutionDecision authorization = policy.authorize(request.config(), toolName);
        if (!authorization.allowed()) {
            recordRuntimeBlock(request, toolName, authorization, 0L);
            throw denied(request, toolName, authorization, elapsed(started));
        }

        McpRuntimeToolSchema schema = schema(request, toolName);
        McpExecutionDecision policyDecision = policy.policyDecision(schema.attributes(), toolName);
        if (!policyDecision.allowed()) {
            recordRuntimeBlock(request, toolName, policyDecision, 0L);
            throw denied(request, toolName, policyDecision, elapsed(started));
        }

        McpExecutionTarget target = policy.target(request.config(), toolName, schema.attributes());
        if (Boolean.TRUE.equals(request.input().get("requireReadOnly")) && !target.readOnly()) {
            McpExecutionDecision restriction = McpExecutionDecision.blocked(
                    "READ_ONLY_REQUIRED", "Independent verification permits reviewed read-only tools only");
            recordRuntimeBlock(request, toolName, restriction, 0L);
            throw denied(request, toolName, restriction, elapsed(started));
        }
        if (!request.trustedLandingRuntime()
                && schema.requiresActivation()
                && !runtime.activated(request.config(), toolName)) {
            McpExecutionDecision denied = McpExecutionDecision.blocked(
                    "MCP_TOOL_NOT_ENABLED",
                    "扩展 MCP 工具尚未在当前 Work Session 激活，请先调用 enable_tool");
            throw denied(request, toolName, denied, elapsed(started));
        }

        McpExecutionDecision route = router.decide(request, target);
        if (!route.allowed()) {
            recordRuntimeBlock(request, toolName, route, 0L);
            throw denied(request, toolName, route, elapsed(started));
        }

        String stage = "APPROVED_LANDING".equals(policy.executionScope(request))
                ? "LANDING" : "PREPARE";
        try {
            String output = remote.call(request.config(), request.rawInput(), stage);
            McpExecutionResponse response = recordResponse(
                    request, toolName, "MCP_REMOTE_TOOL", "SUCCEEDED", output,
                    true, "ALLOWED", elapsed(started));
            audits.record(new McpExecutionAuditPort.McpExecutionAuditEvent(
                    request.config().projectId(), "allowed",
                    target.toolsetId() + "/" + toolName,
                    Map.of("resultId", response.recorded().resultId(), "mcpId", request.config().mcpId())));
            return response;
        } catch (SecurityException error) {
            String code = policy.remoteReasonCode(error.getMessage());
            McpExecutionDecision denied = McpExecutionDecision.blocked(code, message(error, code));
            throw denied(request, toolName, denied, elapsed(started));
        }
    }

    private McpRuntimeToolSchema schema(McpExecutionRequest request, String toolName) {
        try {
            McpRuntimeToolSchema persisted = runtime.schema(request, toolName);
            if (persisted != null && !persisted.missing()) return persisted;
        } catch (SecurityException error) {
            if (!message(error, "").contains("MCP_TOOL_SCHEMA_NOT_HYDRATED")) throw error;
        }
        Map<String, Object> definition = remote.inspect(request.config(), toolName);
        return runtime.hydrate(request, toolName, definition);
    }

    private McpExecutionDeniedException denied(
            McpExecutionRequest request,
            String toolName,
            McpExecutionDecision decision,
            long durationMs) {
        McpExecutionResponse response = blockedResponse(request, toolName, decision, durationMs);
        String message = decision.reasonCode() + "：" + decision.message();
        return new McpExecutionDeniedException(message, response);
    }

    private McpExecutionResponse blockedResponse(
            McpExecutionRequest request,
            String toolName,
            McpExecutionDecision decision,
            long durationMs) {
        Map<String, Object> output = policy.blockedPayload(request.config(), toolName, decision);
        audits.record(new McpExecutionAuditPort.McpExecutionAuditEvent(
                request.config().projectId(), "blocked",
                request.config().toolsetId() + "/" + toolName,
                Map.of("reasonCode", decision.reasonCode(), "runId", request.config().runId())));
        return recordResponse(
                request, toolName, "TOOL_BLOCKED", "BLOCKED", output,
                false, decision.decision(), durationMs);
    }

    private McpExecutionResponse recordResponse(
            McpExecutionRequest request,
            String toolName,
            String source,
            String status,
            Object output,
            boolean allowed,
            String decision,
            long durationMs) {
        McpExecutionRecordedResult recorded = records.record(
                new McpExecutionRecordPort.McpExecutionRecordCommand(
                        request, toolName, source, status, output, durationMs,
                        allowed && !"TOOL_BLOCKED".equals(source)));
        Map<String, Object> payload = new LinkedHashMap<>();
        if (output instanceof Map<?, ?> map) {
            map.forEach((key, value) -> payload.put(String.valueOf(key), value));
        } else {
            String raw = output == null ? "" : String.valueOf(output);
            payload.put("rawPreview", raw.length() <= 2000 ? raw : raw.substring(0, 2000));
            // Structured control results must not be reconstructed from a truncated display preview.
            if (raw.startsWith("{")) {
                try {
                    Map<String, Object> envelope = cn.lgs.orbisops.domain.shared.json.CanonicalJson.parseObject(raw);
                    if ("1".equals(String.valueOf(envelope.get("orbisopsResultVersion")))
                            && envelope.containsKey("normalizedContent")) {
                        payload.put("mcpEnvelope", envelope);
                        payload.put("normalizedContent", envelope.get("normalizedContent"));
                        if (envelope.get("normalizedContent") instanceof Map<?, ?> provider) {
                            payload.put("providerResult", provider);
                        }
                    }
                } catch (IllegalArgumentException invalidLegacyJson) {
                    // Legacy plain text remains display-only; production MCP callbacks validate their envelope.
                }
            }
        }
        return new McpExecutionResponse(
                allowed,
                decision,
                policy.executionScope(request),
                request.config().toolsetId(),
                toolName,
                recorded,
                payload);
    }

    private void recordRuntimeBlock(
            McpExecutionRequest request,
            String toolName,
            McpExecutionDecision decision,
            long durationMs) {
        McpExecutionConfig config = request.config();
        runtime.recordCall(new McpExecutionRuntimePort.McpRuntimeCallEvent(
                config.projectId(), config.agentId(), config.nodeId(), config.runId(),
                config.toolId(), config.mcpId(), toolName, "BLOCKED",
                policy.safeInputSummary(request.rawInput()), decision.details(), durationMs,
                Map.of("reasonCode", decision.reasonCode())));
    }

    private long elapsed(long started) {
        return Math.max(0L, (nanoTimeSupplier.getAsLong() - started) / 1_000_000L);
    }

    private String message(Throwable error, String fallback) {
        String message = error == null ? "" : text(error.getMessage());
        return message.isBlank() ? fallback : message;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private void requireRequest(McpExecutionRequest request) {
        if (request == null) throw new IllegalArgumentException("MCP_EXECUTION_REQUEST_REQUIRED");
    }
}
