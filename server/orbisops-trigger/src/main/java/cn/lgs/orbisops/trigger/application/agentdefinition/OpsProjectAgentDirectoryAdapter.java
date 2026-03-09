package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.application.agentdefinition.ProjectAgentDirectoryPort;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;

import java.util.function.Supplier;

/** Trigger adapter for project existence and project default Agent selection. */
public final class OpsProjectAgentDirectoryAdapter implements ProjectAgentDirectoryPort {

    private final Supplier<ProjectDefinitionApplicationService> projectServiceSupplier;
    private final Supplier<String> platformDefaultAgentIdSupplier;

    public OpsProjectAgentDirectoryAdapter(
            Supplier<ProjectDefinitionApplicationService> projectServiceSupplier,
            Supplier<String> platformDefaultAgentIdSupplier) {
        if (projectServiceSupplier == null) {
            throw new IllegalArgumentException("PROJECT_DEFINITION_SERVICE_SUPPLIER_REQUIRED");
        }
        if (platformDefaultAgentIdSupplier == null) {
            throw new IllegalArgumentException("AGENT_DEFAULT_ID_SUPPLIER_REQUIRED");
        }
        this.projectServiceSupplier = projectServiceSupplier;
        this.platformDefaultAgentIdSupplier = platformDefaultAgentIdSupplier;
    }

    @Override
    public boolean available() {
        return projectServiceSupplier.get() != null;
    }

    @Override
    public boolean exists(String projectId) {
        ProjectDefinitionApplicationService service = projectServiceSupplier.get();
        return service != null && service.exists(projectId);
    }

    @Override
    public String defaultAgentId(String projectId) {
        ProjectDefinitionApplicationService service = projectServiceSupplier.get();
        return service == null
                ? platformDefaultAgentIdSupplier.get()
                : service.defaultAgentId(projectId);
    }
}
