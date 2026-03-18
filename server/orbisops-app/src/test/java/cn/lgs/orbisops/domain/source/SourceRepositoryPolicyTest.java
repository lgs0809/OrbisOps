package cn.lgs.orbisops.domain.source;

import cn.lgs.orbisops.domain.source.model.DeploymentRevisionCandidate;
import cn.lgs.orbisops.domain.source.model.SourceRepositoryCandidate;
import cn.lgs.orbisops.domain.source.service.SourceRepositoryPolicy;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SourceRepositoryPolicyTest {

    private final SourceRepositoryPolicy policy = new SourceRepositoryPolicy();

    @Test
    void preservesRepositoryDefaultsAndNormalizesDeployment() {
        SourceRepositoryCandidate repository = policy.repository(new SourceRepositoryCandidate(
                " project-1 ", "", "", Path.of("/tmp/repo").toString(), ""));
        DeploymentRevisionCandidate deployment = policy.deployment(new DeploymentRevisionCandidate(
                "project-1", "repo-1", " PROD ", " service-1 ", "", " image:v1 "));

        assertEquals("project-1-source", repository.repositoryId());
        assertEquals("project-1-source", repository.name());
        assertEquals("HEAD", repository.defaultRevision());
        assertEquals("prod", deployment.environment());
        assertEquals("service-1", deployment.serviceName());
        assertEquals("image:v1", deployment.imageRef());
    }

    @Test
    void rejectsUnsafeRevisionPathAndSearchQuery() {
        assertEquals("repo-1-readonly-git-mcp", policy.mcpId("repo-1"));
        assertEquals("project-1:prod:service-1",
                policy.deploymentId("project-1", "PROD", "service-1"));
        assertEquals(200, policy.searchLimit(999));
        assertThrows(IllegalArgumentException.class,
                () -> policy.repository(new SourceRepositoryCandidate(
                        "project-1", "repo-1", "repo", "relative/path", "HEAD")));
        assertThrows(IllegalArgumentException.class, () -> policy.sourcePath("../secret"));
        assertThrows(IllegalArgumentException.class, () -> policy.sourcePath(".git/config"));
        assertThrows(IllegalArgumentException.class, () -> policy.searchQuery("bad\nquery"));
        assertThrows(IllegalArgumentException.class,
                () -> policy.repository(new SourceRepositoryCandidate(
                        "project-1", "repo-1", "repo", "/tmp/repo", "feature:unsafe")));
    }
}
