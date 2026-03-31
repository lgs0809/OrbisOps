package cn.lgs.orbisops.trigger.ops.toolset;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public final class OpsLocalToolExecutionHandlerRegistry {

    private final Map<String, OpsLocalToolExecutionHandler> handlers;

    public OpsLocalToolExecutionHandlerRegistry(List<OpsLocalToolExecutionHandler> handlers) {
        if (handlers == null) throw new IllegalArgumentException("LOCAL_ADAPTER_HANDLERS_REQUIRED");
        Map<String, OpsLocalToolExecutionHandler> indexed = new LinkedHashMap<>();
        for (OpsLocalToolExecutionHandler handler : handlers) {
            if (handler == null) throw new IllegalArgumentException("LOCAL_ADAPTER_HANDLER_REQUIRED");
            Set<String> supported = handler.supportedAdapterTypes();
            if (supported == null || supported.isEmpty()) {
                throw new IllegalArgumentException("LOCAL_ADAPTER_HANDLER_TYPES_REQUIRED："
                        + handler.getClass().getName());
            }
            for (String rawType : supported) {
                String adapterType = AbstractOpsSingleLocalToolExecutionHandler.normalize(rawType);
                if (adapterType.isBlank()) {
                    throw new IllegalArgumentException("LOCAL_ADAPTER_TYPE_REQUIRED："
                            + handler.getClass().getName());
                }
                OpsLocalToolExecutionHandler duplicate = indexed.putIfAbsent(adapterType, handler);
                if (duplicate != null) {
                    throw new IllegalStateException("LOCAL_ADAPTER_HANDLER_DUPLICATE：" + adapterType
                            + "：" + duplicate.getClass().getName()
                            + "：" + handler.getClass().getName());
                }
            }
        }
        this.handlers = Map.copyOf(indexed);
    }

    public OpsLocalToolExecutionHandler require(String adapterType) {
        String normalized = AbstractOpsSingleLocalToolExecutionHandler.normalize(adapterType);
        OpsLocalToolExecutionHandler handler = handlers.get(normalized);
        if (handler == null) {
            throw new SecurityException("LOCAL_ADAPTER_NOT_IMPLEMENTED：" + adapterType);
        }
        return handler;
    }

    public Map<String, Object> execute(
            String adapterType,
            String toolName,
            OpsLocalToolArguments arguments) {
        String normalized = AbstractOpsSingleLocalToolExecutionHandler.normalize(adapterType);
        return require(normalized).execute(normalized, toolName, arguments);
    }

    public Set<String> registeredAdapterTypes() {
        return handlers.keySet();
    }
}
