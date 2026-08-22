package cn.lgs.orbisops.trigger.application.source;

import cn.lgs.orbisops.application.source.ProjectServiceApplicationService;
import cn.lgs.orbisops.application.source.SourceAuditPort;
import cn.lgs.orbisops.application.source.SourceExecutionResourcePort;
import cn.lgs.orbisops.application.source.SourceGitPort;
import cn.lgs.orbisops.application.source.SourceMcpProjectionPort;
import cn.lgs.orbisops.application.source.SourceMcpRuntimePort;
import cn.lgs.orbisops.application.source.SourceProjectDirectoryPort;
import cn.lgs.orbisops.application.source.SourceRepositoryApplicationService;
import cn.lgs.orbisops.application.source.SourceTransactionPort;
import cn.lgs.orbisops.domain.source.adapter.repository.IDeploymentRevisionRepository;
import cn.lgs.orbisops.domain.source.adapter.repository.IProjectServiceRepository;
import cn.lgs.orbisops.domain.source.adapter.repository.ISourceRepositoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsSourceApplicationConfiguration {

    @Bean
    public SourceRepositoryApplicationService sourceRepositoryApplicationService(
            ISourceRepositoryRepository repositories,
            IDeploymentRevisionRepository deployments,
            SourceGitPort git,
            SourceProjectDirectoryPort projects,
            SourceMcpProjectionPort projections,
            SourceMcpRuntimePort runtime,
            SourceAuditPort audit,
            SourceTransactionPort transactions,
            @Value("${orbisops.source-repository.enabled:false}") boolean enabled) {
        return new SourceRepositoryApplicationService(
                repositories, deployments, git, projects, projections, runtime, audit, transactions, enabled);
    }

    @Bean
    public ProjectServiceApplicationService projectServiceApplicationService(
            IProjectServiceRepository services,
            ISourceRepositoryRepository repositories,
            SourceProjectDirectoryPort projects,
            SourceExecutionResourcePort executionResources,
            SourceAuditPort audit,
            SourceTransactionPort transactions,
            @Value("${orbisops.source-repository.enabled:false}") boolean enabled) {
        return new ProjectServiceApplicationService(
                services, repositories, projects, executionResources, audit, transactions, enabled);
    }
}
