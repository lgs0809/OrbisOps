package cn.lgs.orbisops.trigger.application.execution;

import cn.lgs.orbisops.application.execution.ExecutionResourceProjectPort;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.application.project.ProjectResourceApplicationService;
import cn.lgs.orbisops.domain.execution.model.ExecutionSourceResource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class OpsExecutionResourceProjectAdapter implements ExecutionResourceProjectPort {

    private final ProjectDefinitionApplicationService projects;
    private final ProjectResourceApplicationService resources;

    public OpsExecutionResourceProjectAdapter(
            ProjectDefinitionApplicationService projects,
            ProjectResourceApplicationService resources) {
        if (projects == null) throw new IllegalArgumentException("PROJECT_DEFINITION_SERVICE_REQUIRED");
        if (resources == null) throw new IllegalArgumentException("PROJECT_RESOURCE_SERVICE_REQUIRED");
        this.projects = projects;
        this.resources = resources;
    }

    @Override
    public boolean exists(String projectId) {
        return projects.exists(projectId);
    }

    @Override
    public List<String> environments(String projectId) {
        List<String> result = projects.environments(projectId);
        return result == null ? List.of() : List.copyOf(result);
    }

    @Override
    public Optional<ExecutionSourceResource> findSourceResource(
            String projectId,
            String resourceId) {
        return resources.findOptionalView(projectId, resourceId)
                .map(this::sourceResource);
    }

    private ExecutionSourceResource sourceResource(Map<String, Object> view) {
        return new ExecutionSourceResource(
                text(view.get("resourceId"), text(view.get("id"), "")),
                text(view.get("type"), ""),
                text(view.get("environment"), ""));
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }
}
