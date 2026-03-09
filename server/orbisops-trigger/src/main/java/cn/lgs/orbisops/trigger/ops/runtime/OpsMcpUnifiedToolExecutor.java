package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import com.alibaba.fastjson.JSON;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Executes MCP callbacks only through the unified Tool Execution boundary. */
final class OpsMcpUnifiedToolExecutor {

    private final Supplier<OpsToolExecutionService> toolExecutionServiceSupplier;
    private final OpsMcpProposedActionFactory proposedActions = new OpsMcpProposedActionFactory();

    OpsMcpUnifiedToolExecutor(Supplier<OpsToolExecutionService> toolExecutionServiceSupplier) {
        if (toolExecutionServiceSupplier == null) {
            throw new IllegalArgumentException("OPS_TOOL_EXECUTION_SERVICE_SUPPLIER_REQUIRED");
        }
        this.toolExecutionServiceSupplier = toolExecutionServiceSupplier;
    }

    Map<String, Object> executeDispatcher(
            OpsMcpServerConfig config,
            String toolInput,
            ToolContext toolContext,
            List<Map<String, Object>> runtimeTools,
            String callbackName) {
        try {
            String requestedToolName = requestedToolName(toolInput);
            boolean readOnly = readOnly(runtimeTools, requestedToolName);
            if (readOnly) {
                return landing(config)
                        ? service().executeLandingMcp(config, toolInput, "ops-agent", true)
                        : service().executeMcp(config, toolInput, "ops-agent", true);
            }
            if (proposedActions.applies(config)) {
                return proposedActions.create(config, requestedToolName, argumentsFromDispatcher(toolInput));
            }
            String idempotencyKey = idempotencyKey(config, callbackName, toolInput, toolContext);
            return landing(config)
                    ? service().executeLandingMcp(config, toolInput, "ops-agent", idempotencyKey)
                    : service().executeMcp(config, toolInput, "ops-agent", idempotencyKey);
        } catch (OpsToolExecutionService.ToolBlockedException error) {
            Map<String, Object> envelope = new LinkedHashMap<>(error.envelope());
            envelope.putIfAbsent("status", "BLOCKED");
            envelope.putIfAbsent("allowed", false);
            envelope.putIfAbsent("remoteCallExecuted", false);
            envelope.putIfAbsent("message", error.getMessage());
            envelope.putIfAbsent("reasonCode", reasonCode(error.getMessage()));
            envelope.put("agentObservation",
                    "MCP 工具调用已被平台策略阻断，远端工具未执行。请在最终报告中说明阻断原因，并提示管理员审核/发布对应 Tool Policy 或改走 ChangePackage。");
            return envelope;
        }
    }

    Map<String, Object> executeDirect(
            OpsMcpServerConfig config,
            String remoteToolName,
            String toolInput,
            ToolContext toolContext,
            boolean readOnly) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("toolName", remoteToolName);
        envelope.put("arguments", arguments(toolInput));
        String routedInput = JSON.toJSONString(envelope);
        if (readOnly) {
            return landing(config)
                    ? service().executeLandingMcp(config, routedInput, "ops-agent", true)
                    : service().executeMcp(config, routedInput, "ops-agent", true);
        }
        if (proposedActions.applies(config)) {
            return proposedActions.create(config, remoteToolName, arguments(toolInput));
        }
        String idempotencyKey = idempotencyKey(config, remoteToolName, toolInput, toolContext);
        return landing(config)
                ? service().executeLandingMcp(config, routedInput, "ops-agent", idempotencyKey)
                : service().executeMcp(config, routedInput, "ops-agent", idempotencyKey);
    }

    private Map<String, Object> arguments(String toolInput) {
        if (!StringUtils.hasText(toolInput)) return Map.of();
        try {
            Map<String, Object> parsed = JSON.parseObject(toolInput, LinkedHashMap.class);
            return parsed == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(parsed));
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("MCP_TOOL_INPUT_MUST_BE_JSON_OBJECT", error);
        }
    }

    private Map<String, Object> argumentsFromDispatcher(String toolInput) {
        if (!StringUtils.hasText(toolInput)) return Map.of();
        try {
            Map<String, Object> parsed = JSON.parseObject(toolInput, LinkedHashMap.class);
            Object value = parsed == null ? null : parsed.get("arguments");
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                map.forEach((key, item) -> result.put(String.valueOf(key), item));
                return Collections.unmodifiableMap(result);
            }
            return Map.of();
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("MCP_TOOL_INPUT_MUST_BE_JSON_OBJECT", error);
        }
    }

    private String requestedToolName(String toolInput) {
        if (!StringUtils.hasText(toolInput)) return "";
        try {
            return value(JSON.parseObject(toolInput).get("toolName"));
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private boolean readOnly(List<Map<String, Object>> runtimeTools, String toolName) {
        if (runtimeTools == null || runtimeTools.isEmpty() || !StringUtils.hasText(toolName)) return false;
        return runtimeTools.stream()
                .filter(item -> toolName.equals(value(item.get("toolName"))))
                .findFirst()
                .map(item -> Boolean.TRUE.equals(item.get("readOnly")))
                .orElse(false);
    }

    private boolean landing(OpsMcpServerConfig config) {
        // Runtime stage is server-created authority. Approval-shaped metadata alone must
        // never elevate an ordinary Chat/Prepare callback into Landing scope.
        return config != null && "LANDING".equalsIgnoreCase(value(config.getToolCallStage()));
    }

    private String idempotencyKey(
            OpsMcpServerConfig config,
            String callbackName,
            String toolInput,
            ToolContext toolContext) {
        String toolCallId = toolCallId(toolContext, callbackName, toolInput);
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("projectId", config == null ? "" : value(config.getProjectId()));
        identity.put("callback", value(callbackName));
        if (landing(config)) {
            // Landing retries may create a new run. Bind mutating idempotency to the
            // approved package + exact call instead of the ephemeral run id, so an
            // UNKNOWN write cannot be blindly replayed by starting another Landing run.
            identity.put("approvedPackageHash",
                    config == null ? "" : value(config.getApprovedPackageHash()));
            identity.put("changePackageId",
                    config == null ? "" : value(config.getChangePackageId()));
            identity.put("input", value(toolInput));
        } else {
            identity.put("runId", config == null ? "" : value(config.getRunId()));
            identity.put("nodeId", config == null ? "" : value(config.getNodeId()));
            if (StringUtils.hasText(toolCallId)) identity.put("toolCallId", toolCallId);
            else identity.put("input", value(toolInput));
        }
        return "mcp:" + CanonicalObjectHasher.sha256(identity);
    }

    private String toolCallId(ToolContext context, String callbackName, String toolInput) {
        if (context == null || context.getToolCallHistory() == null) return "";
        List<Message> history = context.getToolCallHistory();
        for (int messageIndex = history.size() - 1; messageIndex >= 0; messageIndex--) {
            Message message = history.get(messageIndex);
            if (!(message instanceof AssistantMessage assistant)) continue;
            List<AssistantMessage.ToolCall> calls = assistant.getToolCalls();
            for (int callIndex = calls.size() - 1; callIndex >= 0; callIndex--) {
                AssistantMessage.ToolCall call = calls.get(callIndex);
                if (value(callbackName).equals(value(call.name()))
                        && value(toolInput).equals(value(call.arguments()))) {
                    return value(call.id());
                }
            }
        }
        return "";
    }

    private OpsToolExecutionService service() {
        OpsToolExecutionService service = toolExecutionServiceSupplier.get();
        if (service == null) {
            throw new SecurityException("OpsToolExecutionService 未初始化，MCP 工具不能绕过统一执行入口");
        }
        return service;
    }

    private String reasonCode(String message) {
        String text = value(message);
        int delimiter = text.indexOf('：');
        if (delimiter > 0) return text.substring(0, delimiter);
        if (text.contains("MCP_POLICY_PENDING_REVIEW")) return "MCP_POLICY_PENDING_REVIEW";
        if (text.contains("MCP_POLICY_MISSING")) return "MCP_POLICY_MISSING";
        if (text.contains("MCP_TOOL_REQUIRES_CHANGE_PACKAGE")) return "MCP_TOOL_REQUIRES_CHANGE_PACKAGE";
        if (text.contains("TARGET_WRITE_TOOL_REQUIRES_APPROVED_CHANGE_PACKAGE")) {
            return "TARGET_WRITE_TOOL_REQUIRES_APPROVED_CHANGE_PACKAGE";
        }
        return "MCP_TOOL_BLOCKED";
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
