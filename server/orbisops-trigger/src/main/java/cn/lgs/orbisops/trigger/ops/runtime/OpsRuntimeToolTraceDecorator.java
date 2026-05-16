package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.OpsRunCanceledException;
import cn.lgs.orbisops.trigger.ops.OpsRunCancellationRegistry;
import com.alibaba.fastjson.JSON;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Normalizes runtime tool schemas and records controlled invocation lifecycle events. */
public final class OpsRuntimeToolTraceDecorator {

    private final Supplier<OpsRunCancellationRegistry> cancellationRegistrySupplier;

    public OpsRuntimeToolTraceDecorator(
            Supplier<OpsRunCancellationRegistry> cancellationRegistrySupplier) {
        if (cancellationRegistrySupplier == null) {
            throw new IllegalArgumentException("RUN_CANCELLATION_REGISTRY_SUPPLIER_REQUIRED");
        }
        this.cancellationRegistrySupplier = cancellationRegistrySupplier;
    }

    public void decorate(OpsRuntimeResourceContext context) {
        if (context == null) {
            throw new IllegalArgumentException("RUNTIME_RESOURCE_CONTEXT_REQUIRED");
        }
        List<ToolCallback> tools = context.getTools();
        if (tools == null || tools.isEmpty()) {
            return;
        }
        List<ToolCallback> tracedTools = new ArrayList<>();
        for (ToolCallback tool : tools) {
            tracedTools.add(trace(context, tool));
        }
        context.setTools(tracedTools);
        context.getMetadata().put("toolTraceEnabled", true);
        context.getMetadata().put("toolCount", tracedTools.size());
    }

    private ToolCallback trace(
            OpsRuntimeResourceContext context,
            ToolCallback delegate) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return OpsToolSchemaNormalizer.normalize(delegate.getToolDefinition());
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return delegate.getToolMetadata();
            }

            @Override
            public String call(String toolInput) {
                return callWithTrace(context, delegate, toolInput, null);
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return callWithTrace(context, delegate, toolInput, toolContext);
            }
        };
    }

    private String callWithTrace(
            OpsRuntimeResourceContext context,
            ToolCallback delegate,
            String toolInput,
            ToolContext toolContext) {
        String toolName = toolName(delegate);
        long startedNanos = System.nanoTime();
        if (isCanceled(context)) {
            context.record(event(
                    context,
                    "TOOL_CALL_SKIPPED",
                    "CANCELED",
                    "Run 已取消，跳过工具调用：" + toolName,
                    toolPayload(context, toolName, toolInput, null, 0L)));
            throw new OpsRunCanceledException("运维分析任务已取消。");
        }
        context.record(event(
                context,
                "TOOL_CALL_STARTED",
                "RUNNING",
                "工具调用开始：" + toolName,
                toolPayload(context, toolName, toolInput, null, 0L)));
        try {
            String output = toolContext == null
                    ? delegate.call(toolInput)
                    : delegate.call(toolInput, toolContext);
            Map<String, Object> finishedPayload = toolPayload(
                    context,
                    toolName,
                    toolInput,
                    output,
                    elapsedMs(startedNanos));
            boolean proposedOnly = proposedOnly(output);
            boolean blocked = !proposedOnly && blocked(output);
            context.record(event(
                    context,
                    proposedOnly ? "TOOL_CALL_PROPOSED"
                            : blocked ? "TOOL_CALL_BLOCKED" : "TOOL_CALL_FINISHED",
                    proposedOnly ? "PROPOSED" : blocked ? "BLOCKED" : "SUCCEEDED",
                    proposedOnly
                            ? "工具调用已转为 ProposedAction，未执行真实生产变更：" + toolName
                            : blocked
                                    ? "工具调用被治理策略阻断，远端未执行：" + toolName
                                    : "工具调用完成：" + toolName,
                    finishedPayload));
            if (!proposedOnly && !blocked) {
                OpsBusinessResourceIdentityProjector.projectOpenApiResult(
                        context,
                        toolName,
                        toolInput,
                        output);
            }
            if ("PrepareChangePackage".equals(toolName)) {
                Map<String, Object> packagePayload = new LinkedHashMap<>();
                packagePayload.put("changePackageBehavior", "PROPOSE_ONLY");
                packagePayload.put("toolName", toolName);
                packagePayload.put("output", abbreviate(output, 2000));
                context.record(event(
                        context,
                        "CHANGE_PACKAGE_PREPARED",
                        "SUCCEEDED",
                        "已创建可审核 ChangePackage 提案；未执行生产变更。",
                        packagePayload));
            }
            return output;
        } catch (RuntimeException error) {
            Map<String, Object> payload = toolPayload(
                    context,
                    toolName,
                    toolInput,
                    null,
                    elapsedMs(startedNanos));
            payload.put("error", error.getMessage());
            context.record(event(
                    context,
                    "TOOL_CALL_FAILED",
                    "FAILED",
                    "工具调用失败：" + toolName,
                    payload));
            throw error;
        }
    }

    private OpsRuntimeEvent event(
            OpsRuntimeResourceContext context,
            String eventType,
            String status,
            String summary,
            Map<String, Object> payload) {
        return OpsRuntimeEvent.builder()
                .eventType(eventType)
                .nodeId(context.getNode() == null ? null : context.getNode().getNodeId())
                .nodeType(context.getNode() == null ? null : context.getNode().getType())
                .agent(context.getNode() == null ? null : context.getNode().getAgent())
                .status(status)
                .summary(summary)
                .payload(payload)
                .build();
    }

    private boolean isCanceled(OpsRuntimeResourceContext context) {
        OpsRunCancellationRegistry cancellationRegistry =
                cancellationRegistrySupplier.get();
        if (cancellationRegistry == null || context.getRequest() == null) {
            return Thread.currentThread().isInterrupted();
        }
        return cancellationRegistry.isCanceled(context.getRequest().getRunId());
    }

    private Map<String, Object> toolPayload(
            OpsRuntimeResourceContext context,
            String toolName,
            String input,
            String output,
            long durationMs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("owner", context.ownerLabel());
        payload.put("toolKind", toolKind(context, toolName));
        payload.put("toolName", value(toolName));
        payload.put("input", abbreviate(input, 4000));
        if (output != null) {
            payload.put("output", abbreviate(output, 4000));
            payload.put("outputChars", output.length());
            projectStructuredExecutionFacts(payload, output);
        }
        if (durationMs > 0) {
            payload.put("durationMs", durationMs);
        }
        return payload;
    }

    private void projectStructuredExecutionFacts(Map<String, Object> payload, String output) {
        Map<String, Object> envelope = outputEnvelope(output);
        if (envelope.isEmpty()) {
            return;
        }
        copyIfPresent(envelope, payload, "remoteCallExecuted");
        copyIfPresent(envelope, payload, "allowed");
        copyIfPresent(envelope, payload, "resultId");
        copyIfPresent(envelope, payload, "providerId");
        copyIfPresent(envelope, payload, "mcpId");
        copyIfPresent(envelope, payload, "remoteToolName");
        copyIfPresent(envelope, payload, "reasonCode");
        copyIfPresent(envelope, payload, "decision");
        Object status = envelope.get("status");
        if (status != null) {
            payload.put("outputStatus", status);
        }
    }

    private void copyIfPresent(
            Map<String, Object> source,
            Map<String, Object> target,
            String key) {
        if (source.containsKey(key) && source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }

    private boolean proposedOnly(String output) {
        return output != null && output.contains("\"status\":\"REQUIRES_CHANGE_PACKAGE\"")
                && output.contains("\"remoteCallExecuted\":false");
    }

    private boolean blocked(String output) {
        Map<String, Object> envelope = outputEnvelope(output);
        String status = String.valueOf(envelope.getOrDefault("status", "")).trim();
        Object allowed = envelope.get("allowed");
        Object remoteCallExecuted = envelope.get("remoteCallExecuted");
        return "BLOCKED".equalsIgnoreCase(status)
                || (Boolean.FALSE.equals(allowed) && Boolean.FALSE.equals(remoteCallExecuted));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> outputEnvelope(String output) {
        if (output == null || output.isBlank() || !output.trim().startsWith("{")) {
            return Map.of();
        }
        try {
            Map<String, Object> envelope = JSON.parseObject(output, LinkedHashMap.class);
            return envelope == null ? Map.of() : envelope;
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private String toolKind(
            OpsRuntimeResourceContext context,
            String toolName) {
        if ("Skill".equals(toolName)) {
            return "skill";
        }
        if ("PrepareChangePackage".equals(toolName)) {
            return "change-package";
        }
        if ((context.getMcpIds() != null && !context.getMcpIds().isEmpty())
                || (context.getMcpServers() != null && !context.getMcpServers().isEmpty())) {
            return "mcp";
        }
        return "tool";
    }

    private String toolName(ToolCallback callback) {
        ToolDefinition definition = callback == null
                ? null
                : callback.getToolDefinition();
        return definition == null ? "" : definition.name();
    }

    private long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength)) + "...";
    }

    private String value(String value) { return value == null ? "" : value; }
}
