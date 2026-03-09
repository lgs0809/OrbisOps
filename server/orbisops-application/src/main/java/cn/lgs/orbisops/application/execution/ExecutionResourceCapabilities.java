package cn.lgs.orbisops.application.execution;

import java.util.List;

public record ExecutionResourceCapabilities(
        boolean enabled,
        List<String> adapters,
        int resourceCount,
        boolean arbitraryShellAllowed,
        boolean dynamicWorkerConfig) {

    public ExecutionResourceCapabilities {
        adapters = adapters == null ? List.of() : List.copyOf(adapters);
    }
}
