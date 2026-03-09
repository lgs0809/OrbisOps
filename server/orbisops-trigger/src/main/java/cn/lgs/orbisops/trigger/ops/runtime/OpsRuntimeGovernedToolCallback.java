package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/** ToolCallback wrapper that carries explicit runtime authority semantics. */
public final class OpsRuntimeGovernedToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final OpsRuntimeToolAuthorityDescriptor authority;

    private OpsRuntimeGovernedToolCallback(
            ToolCallback delegate,
            OpsRuntimeToolAuthorityDescriptor authority) {
        if (delegate == null) throw new IllegalArgumentException("RUNTIME_TOOL_CALLBACK_REQUIRED");
        if (authority == null) throw new IllegalArgumentException("RUNTIME_TOOL_AUTHORITY_REQUIRED");
        this.delegate = delegate;
        this.authority = authority;
    }

    public static ToolCallback wrap(
            ToolCallback callback,
            OpsRuntimeToolAuthorityDescriptor authority) {
        if (callback instanceof OpsRuntimeGovernedToolCallback governed) {
            if (!governed.authority.equals(authority)) {
                throw new IllegalStateException("RUNTIME_TOOL_AUTHORITY_ALREADY_BOUND_DIFFERENTLY:"
                        + toolName(callback));
            }
            return governed;
        }
        return new OpsRuntimeGovernedToolCallback(callback, authority);
    }

    public static OpsRuntimeToolAuthorityDescriptor authorityOf(ToolCallback callback) {
        return callback instanceof OpsRuntimeGovernedToolCallback governed
                ? governed.authority
                : null;
    }

    public ToolCallback delegate() {
        return delegate;
    }

    public OpsRuntimeToolAuthorityDescriptor authority() {
        return authority;
    }

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
        return delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return delegate.call(toolInput, toolContext);
    }

    private static String toolName(ToolCallback callback) {
        ToolDefinition definition = callback == null ? null : callback.getToolDefinition();
        return definition == null || definition.name() == null ? "" : definition.name().trim();
    }
}
