package cn.lgs.orbisops.trigger.application.execution;

import cn.lgs.orbisops.api.dto.OpsExecutionResourceDTO;
import cn.lgs.orbisops.application.execution.ExecutionAdapterGeneratedTargetQueryPort;
import cn.lgs.orbisops.application.execution.ExecutionAdapterTargetGenerationApplicationService;
import cn.lgs.orbisops.application.execution.ExecutionAdapterTemplateApplicationService;
import cn.lgs.orbisops.application.execution.ExecutionAdapterTemplateCatalogApplicationService;
import cn.lgs.orbisops.application.execution.ExecutionAdapterTemplatePort;
import cn.lgs.orbisops.application.execution.ExecutionAuditPort;
import cn.lgs.orbisops.application.execution.ExecutionResourceCommandApplicationService;
import cn.lgs.orbisops.application.execution.ExecutionResourceProjectPort;
import cn.lgs.orbisops.application.execution.ExecutionResourceQueryApplicationService;
import cn.lgs.orbisops.application.execution.ExecutionResourceRuntimeDirectoryApplicationService;
import cn.lgs.orbisops.application.execution.ExecutionTargetProvisioningPort;
import cn.lgs.orbisops.domain.execution.adapter.repository.IExecutionAdapterTemplateRepository;
import cn.lgs.orbisops.domain.execution.adapter.repository.IExecutionResourceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsExecutionApplicationConfiguration {

    @Bean
    public ExecutionResourceRuntimeDirectoryApplicationService executionResourceRuntimeDirectory(
            IExecutionResourceRepository repository) {
        return new ExecutionResourceRuntimeDirectoryApplicationService(repository);
    }

    @Bean
    public ExecutionResourceCommandApplicationService executionResourceCommandApplicationService(
            IExecutionResourceRepository repository,
            ExecutionResourceRuntimeDirectoryApplicationService directory,
            ExecutionResourceProjectPort projects,
            ExecutionAuditPort auditPort,
            @Value("${orbisops.execution-resource.enabled:true}") boolean enabled) {
        return new ExecutionResourceCommandApplicationService(
                repository, directory, projects, auditPort, enabled);
    }

    @Bean
    public ExecutionResourceQueryApplicationService executionResourceQueryApplicationService(
            ExecutionResourceRuntimeDirectoryApplicationService directory,
            @Value("${orbisops.execution-resource.enabled:true}") boolean enabled) {
        return new ExecutionResourceQueryApplicationService(directory, enabled);
    }

    @Bean
    public ExecutionAdapterTargetGenerationApplicationService<OpsExecutionResourceDTO>
    executionAdapterTargetGenerationApplicationService(
            IExecutionAdapterTemplateRepository repository,
            ExecutionTargetProvisioningPort<OpsExecutionResourceDTO> provisioningPort) {
        return new ExecutionAdapterTargetGenerationApplicationService<>(
                repository, provisioningPort);
    }

    @Bean
    public ExecutionAdapterTemplateCatalogApplicationService
    executionAdapterTemplateCatalogApplicationService(
            IExecutionAdapterTemplateRepository repository,
            ExecutionAdapterGeneratedTargetQueryPort generatedTargetQueryPort) {
        return new ExecutionAdapterTemplateCatalogApplicationService(
                repository, generatedTargetQueryPort);
    }

    @Bean
    public ExecutionAdapterTemplateApplicationService<OpsExecutionResourceDTO>
    executionAdapterTemplateApplicationService(
            ExecutionAdapterTemplatePort<OpsExecutionResourceDTO> port,
            ExecutionAuditPort auditPort) {
        return new ExecutionAdapterTemplateApplicationService<>(port, auditPort);
    }
}
