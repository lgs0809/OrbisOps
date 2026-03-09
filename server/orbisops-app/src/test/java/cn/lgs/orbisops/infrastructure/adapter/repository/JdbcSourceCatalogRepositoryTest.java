package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.source.model.BuildProfile;
import cn.lgs.orbisops.domain.source.model.DeploymentRevision;
import cn.lgs.orbisops.domain.source.model.ProjectService;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JdbcSourceCatalogRepositoryTest {

    @Test
    void explicitTestMemoryStoreSupportsAllTypedRepositories() {
        JdbcSourceCatalogRepository repository = new JdbcSourceCatalogRepository(null, false, true);
        SourceRepository source = source();
        DeploymentRevision deployment = deployment();
        ProjectService service = service();

        repository.save(source);
        repository.save(deployment);
        repository.saveService(service);

        assertTrue(repository.available());
        assertEquals(source, repository.find("project-1", "repo-1").orElseThrow());
        assertEquals(source, repository.findByMcpId("project-1", "repo-1-readonly-git-mcp").orElseThrow());
        assertEquals(deployment, repository.resolve("project-1", "prod", "service-1").orElseThrow());
        assertEquals(service, repository.findService("project-1", "service-1").orElseThrow());
        assertEquals(1, repository.list("project-1").size());
        assertEquals(1, repository.list("project-1", "prod").size());
        assertEquals(1, repository.listServices("project-1").size());
    }

    @Test
    void productionWithoutJdbcIsUnavailableAndFailsClosed() {
        JdbcSourceCatalogRepository repository = new JdbcSourceCatalogRepository(null, true, false);

        assertFalse(repository.available());
        assertThrowsStoreUnavailable(() -> repository.list("project-1"));
        assertThrowsStoreUnavailable(() -> repository.listServices("project-1"));
    }

    @Test
    void jdbcOwnsRepositoryDeploymentServiceUpsertAndJson() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doReturn(1).when(jdbc).update(anyString(), any(Object[].class));
        JdbcSourceCatalogRepository repository = new JdbcSourceCatalogRepository(jdbc, true, false);

        repository.save(source());
        repository.save(deployment());
        repository.saveService(service());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, org.mockito.Mockito.times(3)).update(sql.capture(), args.capture());
        assertTrue(sql.getAllValues().get(0).contains("INSERT INTO ai_ops_source_repository"));
        assertTrue(sql.getAllValues().get(1).contains("INSERT INTO ai_ops_deployment_revision"));
        assertTrue(sql.getAllValues().get(2).contains("INSERT INTO ai_ops_project_service"));
        assertTrue(sql.getAllValues().stream().allMatch(value -> value.contains("ON DUPLICATE KEY UPDATE")));
        assertEquals("[\"https://example.test/smoke\"]", args.getAllValues().get(2)[9]);
    }

    private void assertThrowsStoreUnavailable(Runnable action) {
        assertEquals("代码仓库配置必须使用 MySQL 持久化",
                org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, action::run).getMessage());
    }

    private SourceRepository source() {
        return new SourceRepository(
                "repo-1", "repo-1-readonly-git-mcp", "project-1", "repo", "/tmp/repo", "HEAD",
                "0123456789abcdef0123456789abcdef01234567", "READY", "admin", "now", "now");
    }

    private DeploymentRevision deployment() {
        return new DeploymentRevision(
                "project-1:prod:service-1", "project-1", "repo-1", "prod", "service-1",
                "0123456789abcdef0123456789abcdef01234567", "image:v1", "admin", "now", "now");
    }

    private ProjectService service() {
        return new ProjectService(
                "service-1", "project-1", "service", "repo-1", ".", BuildProfile.MAVEN_VERIFY,
                "", "", "https://example.test/health", List.of("https://example.test/smoke"),
                "READY", "now", "now");
    }
}
