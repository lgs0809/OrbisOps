package cn.lgs.orbisops.trigger.application.repair;

import cn.lgs.orbisops.application.source.ProjectServiceApplicationService;
import cn.lgs.orbisops.application.source.SourceRepositoryApplicationService;
import cn.lgs.orbisops.domain.source.model.BuildProfile;
import cn.lgs.orbisops.domain.source.model.DeploymentRevision;
import cn.lgs.orbisops.domain.source.model.ProjectService;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsRepairSourceCatalogAdapterTest {

    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";

    @Test
    void delegatesTypedSourceFactsWithoutDtoMapping() {
        ProjectServiceApplicationService services = mock(ProjectServiceApplicationService.class);
        SourceRepositoryApplicationService repositories = mock(SourceRepositoryApplicationService.class);
        ProjectService service = new ProjectService(
                "service-1", "project-1", "service", "repo-1", "module",
                BuildProfile.MAVEN_VERIFY, "module/target/app.jar", "", "", List.of(),
                "READY", "now", "now");
        SourceRepository repository = new SourceRepository(
                "repo-1", "repo-1-readonly-git-mcp", "project-1", "repo", "/tmp/repo",
                "HEAD", COMMIT, "READY", "alice", "now", "now");
        DeploymentRevision deployment = new DeploymentRevision(
                "project-1:prod:service-1", "project-1", "repo-1", "prod", "service-1",
                COMMIT, "image:v1", "alice", "now", "now");
        when(services.find("project-1", "service-1")).thenReturn(Optional.of(service));
        when(repositories.find("project-1", "repo-1")).thenReturn(Optional.of(repository));
        when(repositories.resolveDeployment("project-1", "prod", "service-1"))
                .thenReturn(Optional.of(deployment));
        OpsRepairSourceCatalogAdapter adapter =
                new OpsRepairSourceCatalogAdapter(services, repositories);

        assertSame(service, adapter.findService("project-1", "service-1").orElseThrow());
        assertSame(repository, adapter.findRepository("project-1", "repo-1").orElseThrow());
        assertSame(deployment,
                adapter.resolveDeployment("project-1", "prod", "service-1").orElseThrow());

        verify(services).find("project-1", "service-1");
        verify(repositories).find("project-1", "repo-1");
        verify(repositories).resolveDeployment("project-1", "prod", "service-1");
    }
}
