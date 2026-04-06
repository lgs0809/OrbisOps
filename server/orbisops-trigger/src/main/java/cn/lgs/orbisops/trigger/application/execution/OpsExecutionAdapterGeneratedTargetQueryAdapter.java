package cn.lgs.orbisops.trigger.application.execution;

import cn.lgs.orbisops.application.execution.ExecutionAdapterGeneratedTarget;
import cn.lgs.orbisops.application.execution.ExecutionAdapterGeneratedTargetQueryPort;
import cn.lgs.orbisops.application.execution.ExecutionResourceQueryApplicationService;
import cn.lgs.orbisops.domain.execution.model.ExecutionResource;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class OpsExecutionAdapterGeneratedTargetQueryAdapter
        implements ExecutionAdapterGeneratedTargetQueryPort {

    private static final DateTimeFormatter DB_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ExecutionResourceQueryApplicationService resources;

    public OpsExecutionAdapterGeneratedTargetQueryAdapter(
            ExecutionResourceQueryApplicationService resources) {
        if (resources == null) {
            throw new IllegalArgumentException("EXECUTION_RESOURCE_QUERIES_REQUIRED");
        }
        this.resources = resources;
    }

    @Override
    public List<ExecutionAdapterGeneratedTarget> listByTemplate(String templateId) {
        return resources.listByTemplate(templateId).stream()
                .map(this::target)
                .toList();
    }

    private ExecutionAdapterGeneratedTarget target(ExecutionResource resource) {
        return new ExecutionAdapterGeneratedTarget(
                resource.projectId(),
                resource.resourceId(),
                resource.name(),
                resource.adapterTemplateId(),
                resource.adapter().code(),
                resource.workerId(),
                resource.environments(),
                resource.status().name(),
                DB_TIME.format(resource.updatedAt()));
    }
}
