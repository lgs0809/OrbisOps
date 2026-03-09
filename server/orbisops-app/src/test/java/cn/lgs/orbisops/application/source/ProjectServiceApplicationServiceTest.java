package cn.lgs.orbisops.application.source;

import cn.lgs.orbisops.domain.source.adapter.repository.IProjectServiceRepository;
import cn.lgs.orbisops.domain.source.adapter.repository.ISourceRepositoryRepository;
import cn.lgs.orbisops.domain.source.model.ProjectService;
import cn.lgs.orbisops.domain.source.model.ProjectServiceCandidate;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectServiceApplicationServiceTest {

    @Test
    void createsTypedServiceValidatesResourceAndAuditsActor() {
        IProjectServiceRepository services = mock(IProjectServiceRepository.class);
        ISourceRepositoryRepository repositories = mock(ISourceRepositoryRepository.class);
        SourceExecutionResourcePort resources = mock(SourceExecutionResourcePort.class);
        SourceAuditPort audit = mock(SourceAuditPort.class);
        when(services.available()).thenReturn(true);
        when(repositories.available()).thenReturn(true);
        when(repositories.find("project-1", "repo-1")).thenReturn(Optional.of(repository()));
        when(resources.supportsService("project-1", "resource-1", "service-1")).thenReturn(true);
        when(services.findService("project-1", "service-1")).thenReturn(Optional.empty());
        when(services.saveService(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ProjectServiceApplicationService service = service(services, repositories, resources, audit, true);

        ProjectService saved = service.upsert(new ProjectServiceCandidate(
                "project-1", "service-1", "", "repo-1", "", "MAVEN_VERIFY",
                "", "resource-1", "https://example.test/health", List.of()), "admin");

        assertEquals("service-1", saved.name());
        assertEquals(".", saved.modulePath());
        ArgumentCaptor<SourceAuditEvent> event = ArgumentCaptor.forClass(SourceAuditEvent.class);
        verify(audit).record(event.capture());
        assertEquals("create", event.getValue().action());
        ProjectServiceApplicationService.ProjectServiceAuditResult result =
                (ProjectServiceApplicationService.ProjectServiceAuditResult) event.getValue().after();
        assertEquals("admin", result.actor());
    }

    @Test
    void rejectsMissingRepositoryAndUnsupportedExecutionResource() {
        IProjectServiceRepository services = mock(IProjectServiceRepository.class);
        ISourceRepositoryRepository repositories = mock(ISourceRepositoryRepository.class);
        SourceExecutionResourcePort resources = mock(SourceExecutionResourcePort.class);
        when(services.available()).thenReturn(true);
        when(repositories.available()).thenReturn(true);
        ProjectServiceApplicationService service = service(
                services, repositories, resources, mock(SourceAuditPort.class), true);
        ProjectServiceCandidate candidate = new ProjectServiceCandidate(
                "project-1", "service-1", "service", "repo-1", ".", "MAKE_CI",
                "", "resource-1", "", List.of());

        assertEquals("项目代码仓库不存在：repo-1",
                assertThrows(IllegalArgumentException.class,
                        () -> service.upsert(candidate, "admin")).getMessage());

        when(repositories.find("project-1", "repo-1")).thenReturn(Optional.of(repository()));
        assertEquals("部署执行资源不存在、未启用或未配置该服务：resource-1",
                assertThrows(IllegalArgumentException.class,
                        () -> service.upsert(candidate, "admin")).getMessage());
    }

    @Test
    void failsClosedWhenFeatureOrStoreIsUnavailable() {
        IProjectServiceRepository services = mock(IProjectServiceRepository.class);
        ISourceRepositoryRepository repositories = mock(ISourceRepositoryRepository.class);
        ProjectServiceApplicationService disabled = service(
                services, repositories, mock(SourceExecutionResourcePort.class),
                mock(SourceAuditPort.class), false);
        assertThrows(IllegalStateException.class, () -> disabled.list("project-1"));

        ProjectServiceApplicationService enabled = service(
                services, repositories, mock(SourceExecutionResourcePort.class),
                mock(SourceAuditPort.class), true);
        assertEquals("代码仓库配置必须使用 MySQL 持久化",
                assertThrows(IllegalStateException.class, () -> enabled.list("project-1")).getMessage());
    }

    private ProjectServiceApplicationService service(
            IProjectServiceRepository services,
            ISourceRepositoryRepository repositories,
            SourceExecutionResourcePort resources,
            SourceAuditPort audit,
            boolean enabled) {
        SourceTransactionPort transactions = new SourceTransactionPort() {
            @Override
            public <T> T required(java.util.function.Supplier<T> action) {
                return action.get();
            }
        };
        return new ProjectServiceApplicationService(
                services, repositories, projectId -> true, resources, audit, transactions, enabled);
    }

    private SourceRepository repository() {
        return new SourceRepository(
                "repo-1", "repo-1-readonly-git-mcp", "project-1", "repo", "/tmp/repo", "HEAD",
                "0123456789abcdef0123456789abcdef01234567", "READY", "admin", "now", "now");
    }
}
