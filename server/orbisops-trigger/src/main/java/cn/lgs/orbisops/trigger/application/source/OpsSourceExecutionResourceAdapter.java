package cn.lgs.orbisops.trigger.application.source;

import cn.lgs.orbisops.application.execution.ExecutionResourceQueryApplicationService;
import cn.lgs.orbisops.application.source.SourceExecutionResourcePort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class OpsSourceExecutionResourceAdapter implements SourceExecutionResourcePort {

    private final ObjectProvider<ExecutionResourceQueryApplicationService> resources;

    public OpsSourceExecutionResourceAdapter(
            ObjectProvider<ExecutionResourceQueryApplicationService> resources) {
        this.resources = resources;
    }

    @Override
    public boolean supportsService(String projectId, String resourceId, String serviceId) {
        ExecutionResourceQueryApplicationService service = resources.getIfAvailable();
        return service != null && service.supportsService(projectId, resourceId, serviceId);
    }
}
