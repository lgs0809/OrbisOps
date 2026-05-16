package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.config.McpClientCatalogPort;
import cn.lgs.orbisops.application.config.McpClientDefinition;
import cn.lgs.orbisops.application.project.ProjectMcpAuthorizationApplicationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public final class OpsLegacyMcpRuntimeConfigSource implements OpsMcpRuntimeConfigSource {

    public static final String SOURCE_ID = "LEGACY_MCP_CATALOG";
    public static final int ORDER = 200;

    private final ObjectProvider<McpClientCatalogPort> repositories;
    private final ObjectProvider<ProjectMcpAuthorizationApplicationService> authorizations;
    private final OpsLegacyMcpConfigMapper mapper;

    public OpsLegacyMcpRuntimeConfigSource(
            ObjectProvider<McpClientCatalogPort> repositories,
            ObjectProvider<ProjectMcpAuthorizationApplicationService> authorizations,
            OpsLegacyMcpConfigMapper mapper) {
        if (mapper == null) throw new IllegalArgumentException("LEGACY_MCP_CONFIG_MAPPER_REQUIRED");
        this.repositories = repositories;
        this.authorizations = authorizations;
        this.mapper = mapper;
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
        McpClientCatalogPort repository =
                repositories == null ? null : repositories.getIfAvailable();
        if (repository == null) {
            return OpsMcpRuntimeConfigSourceResult.unavailable(
                    "LEGACY_MCP_REPOSITORY_UNAVAILABLE");
        }
        McpClientDefinition record = repository.findByMcpId(request.mcpId());
        if (record == null || !Integer.valueOf(1).equals(record.status())) {
            return OpsMcpRuntimeConfigSourceResult.miss("LEGACY_MCP_NOT_ACTIVE");
        }
        if (!request.projectId().isBlank()) {
            ProjectMcpAuthorizationApplicationService authorization =
                    authorizations == null ? null : authorizations.getIfAvailable();
            if (authorization == null) {
                return OpsMcpRuntimeConfigSourceResult.blocked(
                        "PROJECT_AUTHORIZATION_UNAVAILABLE");
            }
            try {
                if (!authorization.allows(request.projectId(), request.mcpId())) {
                    return OpsMcpRuntimeConfigSourceResult.blocked(
                            "PROJECT_MCP_NOT_AUTHORIZED");
                }
            } catch (RuntimeException error) {
                return OpsMcpRuntimeConfigSourceResult.blocked(
                        "PROJECT_AUTHORIZATION_FAILED:" + summary(error));
            }
        }
        return OpsMcpRuntimeConfigSourceResult.match(mapper.map(record));
    }

    private String summary(Throwable error) {
        if (error == null) return "unknown";
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message.trim();
    }
}
