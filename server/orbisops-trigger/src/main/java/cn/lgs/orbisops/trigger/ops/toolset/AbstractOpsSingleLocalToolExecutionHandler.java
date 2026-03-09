package cn.lgs.orbisops.trigger.ops.toolset;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

abstract class AbstractOpsSingleLocalToolExecutionHandler implements OpsLocalToolExecutionHandler {

    private final String adapterType;

    AbstractOpsSingleLocalToolExecutionHandler(String adapterType) {
        this.adapterType = normalize(adapterType);
        if (this.adapterType.isBlank()) throw new IllegalArgumentException("LOCAL_ADAPTER_TYPE_REQUIRED");
    }

    @Override
    public final Set<String> supportedAdapterTypes() {
        return Set.of(adapterType);
    }

    @Override
    public final Map<String, Object> execute(
            String adapterType,
            String toolName,
            OpsLocalToolArguments arguments) {
        String normalized = normalize(adapterType);
        if (!this.adapterType.equals(normalized)) {
            throw new SecurityException("LOCAL_ADAPTER_HANDLER_MISMATCH：" + adapterType);
        }
        if (arguments == null) throw new IllegalArgumentException("LOCAL_TOOL_ARGUMENTS_REQUIRED");
        return executeRequired(toolName, arguments);
    }

    protected abstract Map<String, Object> executeRequired(
            String toolName,
            OpsLocalToolArguments arguments);

    static String normalize(String adapterType) {
        return adapterType == null ? "" : adapterType.trim().toUpperCase(Locale.ROOT);
    }
}
