package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Assembles MCP callbacks without owning progressive invocation or remote execution. */
@Slf4j
@Service
public final class OpsMcpToolCallbackAssembler {

    private final OpsMcpCallbackPolicyAdapter callbackPolicy;
    private final OpsProgressiveMcpCallbackAdapter progressiveCallbacks;
    private final OpsMcpProgressiveRuntimeAdapter progressiveRuntime;
    private final OpsMcpRuntimeInvoker runtimeInvoker;
    private final OpsLandingMcpModelProjection landingProjection = new OpsLandingMcpModelProjection();

    public OpsMcpToolCallbackAssembler(
            OpsMcpCallbackPolicyAdapter callbackPolicy,
            OpsProgressiveMcpCallbackAdapter progressiveCallbacks,
            OpsMcpProgressiveRuntimeAdapter progressiveRuntime,
            OpsMcpRuntimeInvoker runtimeInvoker) {
        if (callbackPolicy == null) throw new IllegalArgumentException("MCP_CALLBACK_POLICY_REQUIRED");
        if (progressiveCallbacks == null) throw new IllegalArgumentException("MCP_PROGRESSIVE_CALLBACK_ADAPTER_REQUIRED");
        if (progressiveRuntime == null) throw new IllegalArgumentException("MCP_PROGRESSIVE_RUNTIME_REQUIRED");
        if (runtimeInvoker == null) throw new IllegalArgumentException("MCP_RUNTIME_INVOKER_REQUIRED");
        this.callbackPolicy = callbackPolicy;
        this.progressiveCallbacks = progressiveCallbacks;
        this.progressiveRuntime = progressiveRuntime;
        this.runtimeInvoker = runtimeInvoker;
    }

    public List<ToolCallback> assemble(List<OpsMcpServerConfig> configs) {
        if (configs == null || configs.isEmpty()) return List.of();
        List<ToolCallback> callbacks = new ArrayList<>();
        for (OpsMcpServerConfig config : configs) {
            if (config == null || !StringUtils.hasText(config.getName())) continue;
            try {
                assembleServer(config, callbacks);
            } catch (Exception error) {
                log.warn("构建节点 MCP 工具失败，server={}，error={}",
                        config.getName(), error.getMessage());
            }
        }
        return List.copyOf(callbacks);
    }

    public List<Map<String,Object>> currentAuthorizedDefinitions(OpsMcpServerConfig config) {
        var allowed = progressiveRuntime.currentlyAuthorizedNames(config);
        if (allowed.isEmpty()) return List.of();
        return runtimeInvoker.currentLocalDefinitions(config).stream()
                .filter(tool -> allowed.contains(String.valueOf(tool.get("name"))))
                .map(tool -> landingProjection.definition(config, tool)).toList();
    }

    public Map<String,Object> currentAuthorizedDefinition(OpsMcpServerConfig config,String name) {
        if (!progressiveRuntime.authorizedForCurrentStage(config,name)) return Map.of();
        return landingProjection.definition(config, runtimeInvoker.currentLocalDefinition(config,name));
    }

    private void assembleServer(
            OpsMcpServerConfig config,
            List<ToolCallback> callbacks) {
        OpsMcpProgressiveExposure exposure = progressiveRuntime.exposure(config);
        if (exposure.blocked()) return;
        if (exposure.managed()) {
            config.setAllowedTools(exposure.allowedToolNames());
            if (exposure.disclosureEnabled()) {
                callbacks.add(governed(
                        progressiveCallbacks.catalogCallback(config),
                        OpsRuntimeToolAuthorityDescriptor.readOnly(
                                "MCP_PROGRESSIVE_CATALOG",
                                Set.of(
                                        AgentExecutionStage.INVESTIGATE,
                                        AgentExecutionStage.PREPARE,
                                        AgentExecutionStage.LANDING),
                                aliases(config))));
                // CORE also needs a way to inspect parameters before the model can call it.
                if (!exposure.runtimeTools().isEmpty()) {
                    callbacks.add(governed(
                            progressiveCallbacks.enableCallback(config),
                            OpsRuntimeToolAuthorityDescriptor.delegated(
                                    "MCP_PROGRESSIVE_ENABLE",
                                    Set.of(
                                            AgentExecutionStage.INVESTIGATE,
                                            AgentExecutionStage.PREPARE,
                                            AgentExecutionStage.LANDING),
                                    aliases(config))));
                }
            }
            callbacks.add(governed(
                    progressiveCallbacks.dispatcherCallback(
                            config,
                            exposure.runtimeTools()),
                    OpsRuntimeToolAuthorityDescriptor.delegated(
                            "MCP_PROGRESSIVE_DISPATCHER",
                            Set.of(
                                    AgentExecutionStage.INVESTIGATE,
                                    AgentExecutionStage.PREPARE,
                                    AgentExecutionStage.LANDING),
                            aliases(config))));
            return;
        }

        OpsMcpRemoteClientAdapter.Session session = runtimeInvoker.open(config);
        for (ToolCallback callback : session.callbacks()) {
            String capability = callbackPolicy.declaredCapability(config, callback);
            if (!callbackPolicy.allowed(callback, capability)) {
                log.warn("{} capability={}", callbackPolicy.blockedReason(callback), value(capability));
                continue;
            }
            ToolCallback routed = progressiveCallbacks.directCallback(
                    config,
                    callback,
                    readOnly(config, capability));
            ToolCallback decorated = callbackPolicy.decorate(routed, capability);
            callbacks.add(governed(decorated, directAuthority(config, decorated, capability)));
        }
    }

    private ToolCallback governed(
            ToolCallback callback,
            OpsRuntimeToolAuthorityDescriptor descriptor) {
        return OpsRuntimeGovernedToolCallback.wrap(callback, descriptor);
    }

    private OpsRuntimeToolAuthorityDescriptor directAuthority(
            OpsMcpServerConfig config,
            ToolCallback callback,
            String capability) {
        boolean readOnly = readOnly(config, capability);
        Set<String> aliases = aliases(config);
        String callbackName = callback == null || callback.getToolDefinition() == null
                ? ""
                : value(callback.getToolDefinition().name());
        if (!callbackName.isBlank()) {
            LinkedHashSet<String> expanded = new LinkedHashSet<>(aliases);
            expanded.add(callbackName);
            aliases = Set.copyOf(expanded);
        }
        if (readOnly) {
            return OpsRuntimeToolAuthorityDescriptor.readOnly(
                    "MCP_DIRECT",
                    Set.of(
                            AgentExecutionStage.INVESTIGATE,
                            AgentExecutionStage.PREPARE,
                            AgentExecutionStage.LANDING),
                    aliases);
        }
        return OpsRuntimeToolAuthorityDescriptor.targetWrite(
                "MCP_DIRECT",
                production(config)
                        ? Set.of(AgentExecutionStage.LANDING)
                        : Set.of(AgentExecutionStage.PREPARE, AgentExecutionStage.LANDING),
                aliases);
    }

    private boolean readOnly(OpsMcpServerConfig config, String capability) {
        String normalized = value(capability).toUpperCase(java.util.Locale.ROOT);
        if (Set.of("READ", "READ_ONLY", "READONLY", "QUERY", "SEARCH", "LIST", "GET",
                "EVIDENCE", "OBSERVE", "INSPECT").contains(normalized)) {
            return true;
        }
        if (Set.of("WRITE", "MUTATING", "MUTATE", "UPDATE", "CREATE", "DELETE", "INSERT",
                "EXECUTE", "APPLY", "DEPLOY", "PATCH", "RESTART", "CONFIG", "CONFIGURE", "DDL")
                .contains(normalized)) {
            return false;
        }
        Map<String, String> capabilities = config == null || config.getToolCapabilities() == null
                ? Map.of()
                : config.getToolCapabilities();
        return "true".equalsIgnoreCase(value(capabilities.get("readOnly")));
    }

    private boolean production(OpsMcpServerConfig config) {
        Map<String, String> capabilities = config == null || config.getToolCapabilities() == null
                ? Map.of()
                : config.getToolCapabilities();
        String environment = value(capabilities.get("resourceEnvironment")).toLowerCase(java.util.Locale.ROOT);
        return "prod".equals(environment) || "production".equals(environment);
    }

    private Set<String> aliases(OpsMcpServerConfig config) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (config != null) {
            add(values, config.getToolId());
            add(values, config.getMcpId());
            add(values, config.getName());
        }
        return Set.copyOf(values);
    }

    private void add(Set<String> target, Object value) {
        String normalized = value(value);
        if (!normalized.isBlank()) target.add(normalized);
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
