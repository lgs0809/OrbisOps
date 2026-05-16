package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.mcp.McpCommands;
import cn.lgs.orbisops.application.mcp.ProgressiveMcpProcessManager;
import cn.lgs.orbisops.domain.evidence.service.SensitiveDataRedactionPolicy;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Owns serialized MCP invocation, isolation, redaction, resilience and runtime audit. */
@Slf4j
public final class OpsMcpRemoteInvocationAdapter {

    private static final Pattern SENSITIVE_KEY_PATTERN = Pattern.compile(
            "(?i).*(secret|token|password|credential|private|key).*");
    private static final SensitiveDataRedactionPolicy REDACTION =
            new SensitiveDataRedactionPolicy();

    private final OpsMcpRemoteClientAdapter remoteClientAdapter;
    private final Supplier<ProgressiveMcpProcessManager> progressiveMcpProcessManagerSupplier;
    private final OpsMcpInvocationResiliencePolicy resilience;
    private final OpsMcpLogicalCall logicalCall;

    public OpsMcpRemoteInvocationAdapter(
            OpsMcpRemoteClientAdapter remoteClientAdapter,
            Supplier<ProgressiveMcpProcessManager> progressiveMcpProcessManagerSupplier) {
        this(
                remoteClientAdapter,
                progressiveMcpProcessManagerSupplier,
                new OpsMcpInvocationResiliencePolicy(
                        3,
                        Duration.ofSeconds(30),
                        Clock.systemUTC(),
                        new OpsMcpRuntimeSloTelemetry(
                                (io.micrometer.core.instrument.MeterRegistry) null)));
    }

    public OpsMcpRemoteInvocationAdapter(
            OpsMcpRemoteClientAdapter remoteClientAdapter,
            Supplier<ProgressiveMcpProcessManager> progressiveMcpProcessManagerSupplier,
            OpsMcpInvocationResiliencePolicy resilience) {
        if (remoteClientAdapter == null) {
            throw new IllegalArgumentException("MCP_REMOTE_CLIENT_ADAPTER_REQUIRED");
        }
        if (progressiveMcpProcessManagerSupplier == null) {
            throw new IllegalArgumentException("PROGRESSIVE_MCP_PROCESS_MANAGER_SUPPLIER_REQUIRED");
        }
        if (resilience == null) {
            throw new IllegalArgumentException("MCP_INVOCATION_RESILIENCE_REQUIRED");
        }
        this.remoteClientAdapter = remoteClientAdapter;
        this.progressiveMcpProcessManagerSupplier = progressiveMcpProcessManagerSupplier;
        this.resilience = resilience;
        this.logicalCall = new OpsMcpLogicalCall(remoteClientAdapter, this::attempt);
    }

    public ToolCallback serialized(
            OpsMcpRemoteClientAdapter.Session session,
            ToolCallback delegate) {
        if (session == null) {
            throw new IllegalArgumentException("MCP_REMOTE_CLIENT_SESSION_REQUIRED");
        }
        if (delegate == null) {
            throw new IllegalArgumentException("MCP_REMOTE_TOOL_CALLBACK_REQUIRED");
        }
        String toolName = toolName(delegate);
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return delegate.getToolDefinition();
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return delegate.getToolMetadata();
            }

            @Override
            public String call(String toolInput) {
                return logicalCall.invoke(session.config(), toolName, toolInput, session, delegate);
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return logicalCall.invoke(session.config(), toolName, toolInput, session, delegate);
            }
        };
    }

    public String invoke(
            OpsMcpServerConfig config,
            String remoteToolName,
            String remoteArgs) {
        return logicalCall.invoke(config, remoteToolName, remoteArgs, null, null);
    }

    private String attempt(OpsMcpRemoteClientAdapter.Session session, ToolCallback callback, String input) {
        return callSerialized(session.handle(), session.config(), toolName(callback), input, () -> callback.call(input));
    }

    public String summarizeInput(String toolInput) {
        if (!StringUtils.hasText(toolInput)) return "{}";
        try {
            Map<String, Object> input = JSON.parseObject(toolInput, LinkedHashMap.class);
            Map<String, Object> summary = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : input.entrySet()) {
                String key = value(entry.getKey());
                Object item = entry.getValue();
                if (SENSITIVE_KEY_PATTERN.matcher(key).matches()) {
                    summary.put(key, "<redacted>");
                } else if (item instanceof Number || item instanceof Boolean) {
                    summary.put(key, item);
                } else if (item instanceof List<?> list) {
                    summary.put(key, "list(size=" + list.size() + ")");
                } else if (item instanceof Map<?, ?> map) {
                    summary.put(key, "object(keys=" + map.keySet() + ")");
                } else {
                    summary.put(key,
                            "text(chars=" + (item == null ? 0 : String.valueOf(item).length()) + ")");
                }
            }
            return JSON.toJSONString(summary);
        } catch (Exception ignored) {
            return "{unparsedChars=" + toolInput.length() + "}";
        }
    }

    private String callSerialized(
            OpsMcpClientRegistry.ClientHandle handle,
            OpsMcpServerConfig config,
            String toolName,
            String toolInput,
            Callable<String> call) {
        OpsMcpInvocationResiliencePolicy.Permit permit;
        try {
            permit = resilience.acquire(config, toolName);
        } catch (RuntimeException rejected) {
            recordMcpCall(
                    handle,
                    config,
                    toolName,
                    "REJECTED",
                    toolInput,
                    REDACTION.redactText(value(rejected.getMessage())),
                    0L);
            throw rejected;
        }
        long startedNanos = System.nanoTime();
        OpsMcpRequestScope scope = new OpsMcpRequestScope(config);
        boolean locked = false;
        try {
            OpsMcpRequestScope.acquire(handle.lock());
            locked = true;
            handle.assertValid();
            assertAuthorityFresh(config);
            handle.touch();
            String result = call.call();
            long durationMs = elapsedMs(startedNanos);
            resilience.succeeded(permit, durationMs);
            recordMcpCall(
                    handle,
                    config,
                    toolName,
                    "SUCCEEDED",
                    toolInput,
                    REDACTION.redact(result),
                    durationMs);
            log.info("MCP 工具调用完成，tool={}，input={}，durationMs={}，resultChars={}，emptyResult={}",
                    value(toolName),
                    summarizeInput(toolInput),
                    durationMs,
                    result == null ? 0 : result.length(),
                    !StringUtils.hasText(result) || "null".equalsIgnoreCase(result.trim()));
            return result;
        } catch (RuntimeException error) {
            recordFailure(handle, config, toolName, toolInput, startedNanos, permit, error);
            throw error;
        } catch (Exception error) {
            recordFailure(handle, config, toolName, toolInput, startedNanos, permit, error);
            throw new IllegalStateException(
                    "MCP 工具调用失败：" + REDACTION.redactText(value(error.getMessage())),
                    error);
        } finally {
            scope.close();
            handle.touch();
            if (locked) handle.lock().unlock();
        }
    }

    private void recordFailure(
            OpsMcpClientRegistry.ClientHandle handle,
            OpsMcpServerConfig config,
            String toolName,
            String toolInput,
            long startedNanos,
            OpsMcpInvocationResiliencePolicy.Permit permit,
            Exception error) {
        long durationMs = elapsedMs(startedNanos);
        try {
            if (OpsMcpFailureClassifier.dependencyFailure(error)) resilience.failed(permit, durationMs);
            else resilience.neutral(permit);
        } catch (RuntimeException resilienceFailure) {
            error.addSuppressed(resilienceFailure);
        }
        String redactedError = REDACTION.redactText(value(error.getMessage()));
        Object output = error instanceof OpsMcpCallFailure typed
                ? Map.of("errorClass", typed.kind().name(), "dispatched", typed.dispatched(), "message", redactedError,
                        "rawEnvelope", typed.rawEnvelope() == null ? "" : REDACTION.redact(typed.rawEnvelope())) : redactedError;
        recordMcpCall(handle, config, toolName, "FAILED", toolInput, output, durationMs);
        log.warn("MCP 工具调用失败，tool={}，durationMs={}，error={}",
                value(toolName), durationMs, redactedError);
    }

    private void recordMcpCall(
            OpsMcpClientRegistry.ClientHandle handle,
            OpsMcpServerConfig config,
            String toolName,
            String status,
            String toolInput,
            Object output,
            long durationMs) {
        try {
            ProgressiveMcpProcessManager processManager = progressiveMcpProcessManagerSupplier.get();
            if (processManager == null || handle == null || config == null) return;
            processManager.recordMcpCall(new McpCommands.RuntimeCall(
                    value(config.getProjectId()),
                    value(config.getAgentId()),
                    value(config.getNodeId()),
                    value(config.getRunId()),
                    value(config.getToolId(),
                            value(config.getMcpId(),
                                    StringUtils.hasText(config.getName())
                                            ? config.getName() + ":" + value(toolName)
                                            : value(toolName))),
                    value(config.getMcpId(), config.getName()),
                    value(toolName),
                    status,
                    summarizeInput(toolInput),
                    REDACTION.redact(output),
                    durationMs,
                    OpsMcpRequestScope.audit()));
        } catch (Exception error) {
            log.debug("MCP 调用审计写入失败，已忽略：{}",
                    REDACTION.redactText(value(error.getMessage())));
        }
    }

    private void assertAuthorityFresh(OpsMcpServerConfig config) {
        Instant deadline = config == null ? null : config.getAuthorityDeadline();
        if (deadline != null && !deadline.isAfter(Instant.now())) {
            throw new SecurityException("AGENT_RUN_EXECUTION_CONTEXT_EXPIRED_AFTER_MCP_LOCK");
        }
    }

    private long elapsedMs(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private String toolName(ToolCallback callback) {
        ToolDefinition definition = callback == null ? null : callback.getToolDefinition();
        return definition == null ? "" : value(definition.name());
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String value(Object value, String fallback) {
        String text = value(value);
        return StringUtils.hasText(text) ? text : value(fallback);
    }
}
