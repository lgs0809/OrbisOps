package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Compatibility facade. Callback assembly, progressive coordination and remote
 * runtime invocation are owned by dedicated services.
 */
@Service
public class OpsMcpToolProvider {

    private final OpsMcpToolCallbackAssembler callbackAssembler;
    private final OpsProgressiveMcpInvocationService progressiveInvocations;
    private final OpsMcpRuntimeInvoker runtimeInvoker;

    @Autowired
    public OpsMcpToolProvider(
            OpsMcpToolCallbackAssembler callbackAssembler,
            OpsProgressiveMcpInvocationService progressiveInvocations,
            OpsMcpRuntimeInvoker runtimeInvoker) {
        if (callbackAssembler == null) {
            throw new IllegalArgumentException("MCP_TOOL_CALLBACK_ASSEMBLER_REQUIRED");
        }
        if (progressiveInvocations == null) {
            throw new IllegalArgumentException("MCP_PROGRESSIVE_INVOCATION_SERVICE_REQUIRED");
        }
        if (runtimeInvoker == null) {
            throw new IllegalArgumentException("MCP_RUNTIME_INVOKER_REQUIRED");
        }
        this.callbackAssembler = callbackAssembler;
        this.progressiveInvocations = progressiveInvocations;
        this.runtimeInvoker = runtimeInvoker;
    }

    /** Compatibility constructor retained for package-local tests and adapters. */
    OpsMcpToolProvider(
            OpsMcpCallbackPolicyAdapter callbackPolicyAdapter,
            OpsProgressiveMcpCallbackAdapter progressiveCallbackAdapter,
            OpsMcpRemoteCallPolicy remoteCallPolicy,
            OpsMcpRemoteClientAdapter remoteClientAdapter,
            OpsMcpRemoteInvocationAdapter remoteInvocationAdapter,
            OpsMcpProgressiveRuntimeAdapter progressiveRuntimeAdapter) {
        this(legacy(
                callbackPolicyAdapter,
                progressiveCallbackAdapter,
                remoteCallPolicy,
                remoteClientAdapter,
                remoteInvocationAdapter,
                progressiveRuntimeAdapter));
    }

    private OpsMcpToolProvider(LegacyComponents components) {
        this(
                components.callbackAssembler(),
                components.progressiveInvocations(),
                components.runtimeInvoker());
    }

    public List<ToolCallback> buildToolCallbacks(List<OpsMcpServerConfig> configs) {
        return callbackAssembler.assemble(configs);
    }

    public java.util.List<Map<String,Object>> currentAuthorizedDefinitions(OpsMcpServerConfig config) {
        return callbackAssembler.currentAuthorizedDefinitions(config);
    }

    public Map<String,Object> currentAuthorizedDefinition(OpsMcpServerConfig config,String name) {
        return callbackAssembler.currentAuthorizedDefinition(config,name);
    }

    public String callProgressiveDirect(OpsMcpServerConfig config, String toolInput) {
        return progressiveInvocations.invoke(config, toolInput, this::invokeRemoteMcpTool);
    }

    public Map<String, Object> inspectRemoteToolDefinition(
            OpsMcpServerConfig config,
            String remoteToolName) {
        return runtimeInvoker.inspectDefinition(config, remoteToolName);
    }

    public List<Map<String, Object>> inspectRemoteToolDefinitions(OpsMcpServerConfig config) {
        return runtimeInvoker.inspectDefinitions(config);
    }

    protected String callRemoteMcpTool(
            OpsMcpServerConfig config,
            String remoteToolName,
            String remoteArgs) {
        return invokeRemoteMcpTool(config, remoteToolName, remoteArgs);
    }

    protected String invokeRemoteMcpTool(
            OpsMcpServerConfig config,
            String remoteToolName,
            String remoteArgs) {
        return runtimeInvoker.invoke(config, remoteToolName, remoteArgs);
    }

    public void invalidateAll() {
        runtimeInvoker.invalidateAll();
    }

    private static LegacyComponents legacy(
            OpsMcpCallbackPolicyAdapter callbackPolicyAdapter,
            OpsProgressiveMcpCallbackAdapter progressiveCallbackAdapter,
            OpsMcpRemoteCallPolicy remoteCallPolicy,
            OpsMcpRemoteClientAdapter remoteClientAdapter,
            OpsMcpRemoteInvocationAdapter remoteInvocationAdapter,
            OpsMcpProgressiveRuntimeAdapter progressiveRuntimeAdapter) {
        OpsMcpRuntimeInvoker runtimeInvoker = new OpsMcpRuntimeInvoker(
                remoteClientAdapter,
                remoteInvocationAdapter);
        return new LegacyComponents(
                new OpsMcpToolCallbackAssembler(
                        callbackPolicyAdapter,
                        progressiveCallbackAdapter,
                        progressiveRuntimeAdapter,
                        runtimeInvoker),
                new OpsProgressiveMcpInvocationService(
                        callbackPolicyAdapter,
                        remoteCallPolicy,
                        progressiveRuntimeAdapter,
                        runtimeInvoker),
                runtimeInvoker);
    }

    private record LegacyComponents(
            OpsMcpToolCallbackAssembler callbackAssembler,
            OpsProgressiveMcpInvocationService progressiveInvocations,
            OpsMcpRuntimeInvoker runtimeInvoker) {
    }
}
