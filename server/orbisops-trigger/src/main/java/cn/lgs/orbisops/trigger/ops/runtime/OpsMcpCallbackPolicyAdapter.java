package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/** Applies configured MCP capability ACLs before callbacks enter the typed runtime authority gate. */
@Component
public final class OpsMcpCallbackPolicyAdapter {

    private final OpsToolExecutionPolicy toolExecutionPolicy;

    public OpsMcpCallbackPolicyAdapter(OpsToolExecutionPolicy toolExecutionPolicy) {
        if (toolExecutionPolicy == null) {
            throw new IllegalArgumentException("OPS_TOOL_EXECUTION_POLICY_REQUIRED");
        }
        this.toolExecutionPolicy = toolExecutionPolicy;
    }

    public boolean allowed(ToolCallback callback, String declaredCapability) {
        return toolExecutionPolicy.allowTool(callback, declaredCapability);
    }

    public String blockedReason(ToolCallback callback) {
        return toolExecutionPolicy.blockedReason(callback);
    }

    public void assertAllowed(OpsMcpServerConfig config, ToolCallback callback) {
        String capability = declaredCapability(config, callback);
        if (!allowed(callback, capability)) {
            throw new SecurityException(blockedReason(callback));
        }
    }

    public ToolCallback decorate(ToolCallback delegate, String capability) {
        if (!StringUtils.hasText(capability)) {
            return delegate;
        }
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                ToolDefinition definition = delegate.getToolDefinition();
                if (definition == null) {
                    return null;
                }
                String suffix = "\n\nOps capability: " + capability + ".";
                String description = value(definition.description());
                return ToolDefinition.builder()
                        .name(definition.name())
                        .description(description.contains("Ops capability:")
                                ? description
                                : description + suffix)
                        .inputSchema(definition.inputSchema())
                        .build();
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return delegate.getToolMetadata();
            }

            @Override
            public String call(String toolInput) {
                return delegate.call(toolInput);
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return delegate.call(toolInput, toolContext);
            }
        };
    }

    public String declaredCapability(OpsMcpServerConfig config, ToolCallback callback) {
        String name = toolName(callback);
        if (!StringUtils.hasText(name)) {
            return "";
        }
        if (matchesTool(config == null ? null : config.getBlockedTools(), name)) {
            return "blocked";
        }
        if (matchesTool(config == null ? null : config.getNotificationTools(), name)) {
            return "notification";
        }
        Map<String, String> capabilities = config == null || config.getToolCapabilities() == null
                ? Map.of()
                : config.getToolCapabilities();
        String direct = capabilities.get(name);
        if (!StringUtils.hasText(direct)) {
            direct = capabilities.get(name.toLowerCase());
        }
        if (!StringUtils.hasText(direct)) {
            direct = capabilities.get("*");
        }
        return direct;
    }

    public boolean matchesTool(List<String> configuredTools, String toolName) {
        if (configuredTools == null || configuredTools.isEmpty() || !StringUtils.hasText(toolName)) {
            return false;
        }
        String normalized = toolName.toLowerCase();
        return configuredTools.stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toLowerCase())
                .anyMatch(value -> "*".equals(value) || normalized.equals(value));
    }

    private String toolName(ToolCallback callback) {
        ToolDefinition definition = callback == null ? null : callback.getToolDefinition();
        return definition == null ? "" : definition.name();
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
