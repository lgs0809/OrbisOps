package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.application.project.ProjectResourcePreparation;
import cn.lgs.orbisops.application.project.ProjectResourcePreparationPort;
import cn.lgs.orbisops.application.project.ProjectResourcePreparationRequest;
import org.springframework.stereotype.Component;

import java.util.Map;

/** ACL for credential handling, live resource discovery and permission enrichment. */
@Component
public class OpsProjectResourcePreparationAdapter
        implements ProjectResourcePreparationPort {

    private final OpsProjectResourcePreparationService preparationService;

    public OpsProjectResourcePreparationAdapter(
            OpsProjectResourcePreparationService preparationService) {
        if (preparationService == null) {
            throw new IllegalArgumentException("PROJECT_RESOURCE_PREPARATION_SERVICE_REQUIRED");
        }
        this.preparationService = preparationService;
    }

    @Override
    public ProjectResourcePreparation prepare(
            ProjectResourcePreparationRequest request) {
        return preparationService.prepare(request);
    }

    @Override
    public Map<String, Object> enrichPermission(
            String type,
            Map<String, Object> permission) {
        return OpsProjectCapabilityMetadataPolicy.enrichPermission(type, permission);
    }
}
