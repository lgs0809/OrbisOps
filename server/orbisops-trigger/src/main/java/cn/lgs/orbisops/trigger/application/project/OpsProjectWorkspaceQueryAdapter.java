package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.application.project.ProjectWorkspaceQueryPort;
import cn.lgs.orbisops.trigger.ops.OpsProjectWorkspaceService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Read-side adapter for the remaining workspace compatibility projection. */
@Component
public class OpsProjectWorkspaceQueryAdapter implements ProjectWorkspaceQueryPort {

    private final OpsProjectWorkspaceService service;

    public OpsProjectWorkspaceQueryAdapter(OpsProjectWorkspaceService service) {
        if (service == null) {
            throw new IllegalArgumentException("PROJECT_WORKSPACE_SERVICE_REQUIRED");
        }
        this.service = service;
    }

    @Override
    public Map<String, Object> snapshot() {
        return service.snapshot();
    }

    @Override
    public List<Map<String, Object>> templates() {
        return service.templates();
    }

    @Override
    public Map<String, Object> detail(String projectId) {
        return service.projectDetail(projectId);
    }
}
