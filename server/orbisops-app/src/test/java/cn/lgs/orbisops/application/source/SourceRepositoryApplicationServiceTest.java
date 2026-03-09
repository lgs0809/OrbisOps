package cn.lgs.orbisops.application.source;

import cn.lgs.orbisops.domain.source.adapter.repository.IDeploymentRevisionRepository;
import cn.lgs.orbisops.domain.source.adapter.repository.ISourceRepositoryRepository;
import cn.lgs.orbisops.domain.source.model.DeploymentRevision;
import cn.lgs.orbisops.domain.source.model.DeploymentRevisionCandidate;
import cn.lgs.orbisops.domain.source.model.SourceFile;
import cn.lgs.orbisops.domain.source.model.SourceMcpRuntimeSpec;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import cn.lgs.orbisops.domain.source.model.SourceRepositoryCandidate;
import cn.lgs.orbisops.domain.source.model.SourceSearchHit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceRepositoryApplicationServiceTest {

    private static final String SHA = "0123456789abcdef0123456789abcdef01234567";

    @Test
    void registersRepositoryInTransactionPublishesMcpAndAudits() {
        ISourceRepositoryRepository repositories = mock(ISourceRepositoryRepository.class);
        IDeploymentRevisionRepository deployments = mock(IDeploymentRevisionRepository.class);
        SourceGitPort git = mock(SourceGitPort.class);
        SourceMcpProjectionPort projection = mock(SourceMcpProjectionPort.class);
        SourceAuditPort audit = mock(SourceAuditPort.class);
        when(repositories.available()).thenReturn(true);
        when(deployments.available()).thenReturn(true);
        when(git.allowedRootsConfigured()).thenReturn(true);
        when(git.resolveCommit("/tmp/repo", "HEAD")).thenReturn(SHA);
        when(repositories.findByRepositoryId("repo-1")).thenReturn(Optional.empty());
        when(repositories.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SourceRepositoryApplicationService service = service(
                repositories, deployments, git, projection, audit, true);

        SourceRepository saved = service.register(new SourceRepositoryCandidate(
                "project-1", "repo-1", "repo", "/tmp/repo", "HEAD"), "admin");

        assertEquals(SHA, saved.defaultCommitSha());
        assertEquals("repo-1-readonly-git-mcp", saved.mcpId());
        verify(projection).publish(saved);
        ArgumentCaptor<SourceAuditEvent> event = ArgumentCaptor.forClass(SourceAuditEvent.class);
        verify(audit).record(event.capture());
        assertEquals("register", event.getValue().action());
        assertEquals("repo-1", event.getValue().targetId());
    }

    @Test
    void recordsDeploymentAndRoutesTypedReadAndSearch() {
        ISourceRepositoryRepository repositories = mock(ISourceRepositoryRepository.class);
        IDeploymentRevisionRepository deployments = mock(IDeploymentRevisionRepository.class);
        SourceGitPort git = mock(SourceGitPort.class);
        SourceRepository repository = repository("project-1", "repo-1");
        when(repositories.available()).thenReturn(true);
        when(deployments.available()).thenReturn(true);
        when(git.allowedRootsConfigured()).thenReturn(true);
        when(repositories.find("project-1", "repo-1")).thenReturn(Optional.of(repository));
        when(git.resolveCommit("/tmp/repo", "release-1")).thenReturn(SHA);
        when(deployments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(git.readFile(repository, SHA, "src/App.java"))
                .thenReturn(new SourceFile("repo-1", SHA, "src/App.java", 10, "class App {}"));
        when(git.search(repository, SHA, "class", 20))
                .thenReturn(List.of(new SourceSearchHit("repo-1", SHA, "src/App.java", 1, "class App {}")));
        SourceRepositoryApplicationService service = service(
                repositories, deployments, git, mock(SourceMcpProjectionPort.class),
                mock(SourceAuditPort.class), true);

        DeploymentRevision deployment = service.recordDeployment(new DeploymentRevisionCandidate(
                "project-1", "repo-1", "PROD", "service-1", "release-1", "image:v1"), "admin");

        assertEquals("project-1:prod:service-1", deployment.deploymentId());
        assertEquals(SHA, service.readFile("project-1", "repo-1", "", "src/App.java").commitSha());
        assertEquals(1, service.search("project-1", "repo-1", "", "class", 20).size());
    }

    @Test
    void failsClosedForDisabledStoreRootsAndCrossProjectId() {
        ISourceRepositoryRepository repositories = mock(ISourceRepositoryRepository.class);
        IDeploymentRevisionRepository deployments = mock(IDeploymentRevisionRepository.class);
        SourceGitPort git = mock(SourceGitPort.class);
        SourceRepositoryApplicationService disabled = service(
                repositories, deployments, git, mock(SourceMcpProjectionPort.class),
                mock(SourceAuditPort.class), false);
        assertThrows(IllegalStateException.class, () -> disabled.list("project-1"));

        when(repositories.available()).thenReturn(true);
        when(deployments.available()).thenReturn(true);
        when(git.allowedRootsConfigured()).thenReturn(true);
        when(repositories.findByRepositoryId("repo-1"))
                .thenReturn(Optional.of(repository("other-project", "repo-1")));
        SourceRepositoryApplicationService enabled = service(
                repositories, deployments, git, mock(SourceMcpProjectionPort.class),
                mock(SourceAuditPort.class), true);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> enabled.register(
                new SourceRepositoryCandidate("project-1", "repo-1", "repo", "/tmp/repo", "HEAD"),
                "admin")).getMessage().contains("其他项目"));
    }

    private SourceRepositoryApplicationService service(
            ISourceRepositoryRepository repositories,
            IDeploymentRevisionRepository deployments,
            SourceGitPort git,
            SourceMcpProjectionPort projection,
            SourceAuditPort audit,
            boolean enabled) {
        SourceMcpRuntimePort runtime = repository -> new SourceMcpRuntimeSpec(
                repository.mcpId(), "description", "stdio", "node", List.of("server.mjs"),
                Map.of(), 8, Map.of("*", "read_only"), List.of("git_read_file"));
        SourceTransactionPort transactions = new SourceTransactionPort() {
            @Override
            public <T> T required(java.util.function.Supplier<T> action) {
                return action.get();
            }
        };
        return new SourceRepositoryApplicationService(
                repositories, deployments, git, projectId -> true, projection, runtime,
                audit, transactions, enabled);
    }

    private SourceRepository repository(String projectId, String repositoryId) {
        return new SourceRepository(
                repositoryId, repositoryId + "-readonly-git-mcp", projectId, "repo", "/tmp/repo",
                "HEAD", SHA, "READY", "admin", "now", "now");
    }
}
