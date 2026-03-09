package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public final class OpsProjectMcpRuntimeConfigSource implements OpsMcpRuntimeConfigSource {

    public static final String SOURCE_ID = "PROJECT_RUNTIME";
    public static final int ORDER = 100;

    private final ObjectProvider<OpsProjectMcpRuntimeConfigService> services;

    public OpsProjectMcpRuntimeConfigSource(
            ObjectProvider<OpsProjectMcpRuntimeConfigService> services) {
        this.services = services;
    }

    @Override
    public String sourceId() {
        return SOURCE_ID;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public OpsMcpRuntimeConfigSourceResult resolve(
            OpsMcpRuntimeConfigRequest request) {
        if (request == null) throw new IllegalArgumentException("MCP_CONFIG_REQUEST_REQUIRED");
        OpsProjectMcpRuntimeConfigService service =
                services == null ? null : services.getIfAvailable();
        if (service == null) {
            return OpsMcpRuntimeConfigSourceResult.unavailable(
                    "PROJECT_RUNTIME_SERVICE_UNAVAILABLE");
        }
        Optional<OpsMcpServerConfig> configured = request.projectId().isBlank()
                ? service.resolveAny(request.mcpId())
                : service.resolve(request.projectId(), request.mcpId());
        return configured
                .map(OpsMcpRuntimeConfigSourceResult::match)
                .orElseGet(() -> OpsMcpRuntimeConfigSourceResult.miss(
                        "PROJECT_RUNTIME_NOT_BOUND"));
    }
}
