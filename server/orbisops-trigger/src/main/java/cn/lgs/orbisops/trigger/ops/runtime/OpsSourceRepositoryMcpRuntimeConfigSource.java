package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.source.OpsSourceRepositoryService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public final class OpsSourceRepositoryMcpRuntimeConfigSource implements OpsMcpRuntimeConfigSource {

    public static final String SOURCE_ID = "SOURCE_REPOSITORY";
    public static final int ORDER = 300;

    private final ObjectProvider<OpsSourceRepositoryService> repositories;

    public OpsSourceRepositoryMcpRuntimeConfigSource(
            ObjectProvider<OpsSourceRepositoryService> repositories) {
        this.repositories = repositories;
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
        OpsSourceRepositoryService repository =
                repositories == null ? null : repositories.getIfAvailable();
        if (repository == null) {
            return OpsMcpRuntimeConfigSourceResult.unavailable(
                    "SOURCE_REPOSITORY_SERVICE_UNAVAILABLE");
        }
        Optional<OpsMcpServerConfig> configured = repository.resolveMcpServer(
                request.projectId(),
                request.mcpId());
        return configured
                .map(OpsMcpRuntimeConfigSourceResult::match)
                .orElseGet(() -> OpsMcpRuntimeConfigSourceResult.miss(
                        "SOURCE_REPOSITORY_MCP_NOT_FOUND"));
    }
}
