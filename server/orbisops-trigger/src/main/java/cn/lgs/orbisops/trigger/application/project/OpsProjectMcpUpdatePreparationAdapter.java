package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.application.project.ProjectMcpUpdatePreparationPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class OpsProjectMcpUpdatePreparationAdapter
        implements ProjectMcpUpdatePreparationPort {

    @Override
    public Map<String, Object> enrichPermission(
            String resourceType,
            Map<String, Object> permission) {
        return OpsProjectCapabilityMetadataPolicy.enrichPermission(
                resourceType, permission);
    }

    @Override
    public Map<String, Object> enrichTransportMetadata(
            String resourceType,
            List<String> allowedActions,
            Map<String, Object> transportConfig) {
        return OpsProjectCapabilityMetadataPolicy.enrichTransportMetadata(
                resourceType, allowedActions, transportConfig);
    }
}
