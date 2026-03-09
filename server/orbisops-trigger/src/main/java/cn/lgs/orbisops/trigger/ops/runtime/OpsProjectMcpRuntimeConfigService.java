package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.project.ProjectMcpRuntimeDescriptorApplicationService;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class OpsProjectMcpRuntimeConfigService {

    private final ProjectMcpRuntimeDescriptorApplicationService descriptorService;
    private final OpsProjectMcpRuntimeConfigFactory runtimeConfigFactory;

    public OpsProjectMcpRuntimeConfigService(
            ProjectMcpRuntimeDescriptorApplicationService descriptorService,
            OpsProjectMcpRuntimeConfigFactory runtimeConfigFactory) {
        if (descriptorService == null) {
            throw new IllegalArgumentException("PROJECT_MCP_RUNTIME_DESCRIPTOR_SERVICE_REQUIRED");
        }
        if (runtimeConfigFactory == null) {
            throw new IllegalArgumentException("PROJECT_MCP_RUNTIME_CONFIG_FACTORY_REQUIRED");
        }
        this.descriptorService = descriptorService;
        this.runtimeConfigFactory = runtimeConfigFactory;
    }

    public Optional<OpsMcpServerConfig> resolve(
            String projectId,
            String mcpId) {
        return descriptorService.resolveEnabled(projectId, mcpId)
                .map(runtimeConfigFactory::build);
    }

    public Optional<OpsMcpServerConfig> resolveAny(String mcpId) {
        return descriptorService.resolveEnabledAny(mcpId)
                .map(runtimeConfigFactory::build);
    }

    public Optional<OpsMcpServerConfig> resolveForDiscovery(
            String projectId,
            String mcpId) {
        return descriptorService.resolveForDiscovery(projectId, mcpId)
                .map(runtimeConfigFactory::build);
    }

    public boolean existsEnabledAny(String mcpId) {
        return descriptorService.existsEnabledAny(mcpId);
    }
}
