package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Coordinates progressive MCP selection, authorization, hydration and invocation. */
@Service
public final class OpsProgressiveMcpInvocationService {

    private final OpsMcpCallbackPolicyAdapter callbackPolicy;
    private final OpsMcpRemoteCallPolicy remoteCallPolicy;
    private final OpsMcpProgressiveRuntimeAdapter progressiveRuntime;
    private final OpsMcpRuntimeInvoker runtimeInvoker;

    public OpsProgressiveMcpInvocationService(
            OpsMcpCallbackPolicyAdapter callbackPolicy,
            OpsMcpRemoteCallPolicy remoteCallPolicy,
            OpsMcpProgressiveRuntimeAdapter progressiveRuntime,
            OpsMcpRuntimeInvoker runtimeInvoker) {
        if (callbackPolicy == null) throw new IllegalArgumentException("MCP_CALLBACK_POLICY_REQUIRED");
        if (remoteCallPolicy == null) throw new IllegalArgumentException("MCP_REMOTE_CALL_POLICY_REQUIRED");
        if (progressiveRuntime == null) throw new IllegalArgumentException("MCP_PROGRESSIVE_RUNTIME_REQUIRED");
        if (runtimeInvoker == null) throw new IllegalArgumentException("MCP_RUNTIME_INVOKER_REQUIRED");
        this.callbackPolicy = callbackPolicy;
        this.remoteCallPolicy = remoteCallPolicy;
        this.progressiveRuntime = progressiveRuntime;
        this.runtimeInvoker = runtimeInvoker;
    }

    public String invoke(OpsMcpServerConfig config, String toolInput) {
        return invoke(config, toolInput, runtimeInvoker::invoke);
    }

    String invoke(
            OpsMcpServerConfig config,
            String toolInput,
            RemoteCall remoteCall) {
        if (config == null) throw new IllegalArgumentException("MCP_SERVER_CONFIG_REQUIRED");
        if (remoteCall == null) throw new IllegalArgumentException("MCP_REMOTE_CALL_REQUIRED");
        long startedNanos = System.nanoTime();
        String remoteToolName = "";
        String remoteArgs = "{}";
        Map<String, Object> callAuditMetadata = new LinkedHashMap<>();
        AtomicBoolean remoteCallStarted = new AtomicBoolean(false);
        try {
            Map<String, Object> input = parse(toolInput);
            remoteToolName = value(input.get("toolName"));
            if (!StringUtils.hasText(remoteToolName)
                    && config.getAllowedTools() != null
                    && config.getAllowedTools().size() == 1) {
                remoteToolName = config.getAllowedTools().get(0);
            }
            if (!StringUtils.hasText(remoteToolName)) {
                throw new IllegalArgumentException(
                        "渐进式 MCP 调用必须提供 toolName，或在项目工具中仅授权一个远端工具。");
            }
            assertAuthorized(config, remoteToolName);
            Object arguments = input.get("arguments");
            remoteArgs = arguments == null
                    ? "{}"
                    : arguments instanceof String text
                            ? text
                            : JSON.toJSONString(arguments);
            Map<String, Object> schema = progressiveRuntime.selectAndHydrate(config, remoteToolName);
            OpsMcpRemoteCallAssessment assessment = remoteCallPolicy.assess(
                    config,
                    remoteToolName,
                    schema);
            callAuditMetadata.putAll(assessment.auditMetadata());
            remoteCallPolicy.assertAllowed(assessment, remoteArgs);
            // A connection/config may serve concurrent tools with different reviewed effects.
            // Never publish per-call authority into that shared configuration.
            OpsMcpServerConfig verified = config.toBuilder().verifiedReadOnly(assessment.readOnly())
                    .verifiedToolSchema(Map.copyOf(schema)).build();
            if (assessment.callbackPolicyRequired()) {
                callbackPolicy.assertAllowed(verified, runtimeInvoker.find(verified, remoteToolName));
            }
            remoteCallStarted.set(true);
            return remoteCall.invoke(verified, remoteToolName, remoteArgs);
        } catch (RuntimeException error) {
            recordPreRemoteFailure(
                    remoteCallStarted.get(), config, remoteToolName,
                    remoteArgs, toolInput, error, startedNanos, callAuditMetadata);
            throw error;
        } catch (Exception error) {
            recordPreRemoteFailure(
                    remoteCallStarted.get(), config, remoteToolName,
                    remoteArgs, toolInput, error, startedNanos, callAuditMetadata);
            throw new IllegalStateException("渐进式 MCP 调用失败：" + error.getMessage(), error);
        }
    }

    private void assertAuthorized(OpsMcpServerConfig config, String remoteToolName) {
        if (callbackPolicy.matchesTool(config.getBlockedTools(), remoteToolName)
                || (!callbackPolicy.matchesTool(config.getAllowedTools(), remoteToolName)
                && !callbackPolicy.matchesTool(config.getNotificationTools(), remoteToolName))) {
            throw new SecurityException("远端 MCP 工具未在当前项目授权范围内：" + remoteToolName);
        }
    }

    private void recordPreRemoteFailure(
            boolean remoteCallStarted,
            OpsMcpServerConfig config,
            String remoteToolName,
            String remoteArgs,
            String toolInput,
            Exception error,
            long startedNanos,
            Map<String, Object> callAuditMetadata) {
        if (remoteCallStarted) return;
        progressiveRuntime.recordPreRemoteFailure(
                config,
                remoteToolName,
                runtimeInvoker.summarizeInput(
                        StringUtils.hasText(remoteArgs) ? remoteArgs : toolInput),
                error,
                (System.nanoTime() - startedNanos) / 1_000_000,
                callAuditMetadata);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String toolInput) {
        if (!StringUtils.hasText(toolInput)) return Map.of();
        Object parsed = JSON.parse(toolInput);
        if (parsed instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            if (!result.containsKey("arguments")) {
                Map<String, Object> arguments = new LinkedHashMap<>(result);
                arguments.remove("toolName");
                result.put("arguments", arguments);
            }
            return result;
        }
        return Map.of("arguments", toolInput);
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    @FunctionalInterface
    interface RemoteCall {
        String invoke(OpsMcpServerConfig config, String remoteToolName, String remoteArgs);
    }
}
