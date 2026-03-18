package cn.lgs.orbisops.domain.source;

import cn.lgs.orbisops.domain.source.model.BuildProfile;
import cn.lgs.orbisops.domain.source.model.ProjectService;
import cn.lgs.orbisops.domain.source.model.ProjectServiceBuildCommand;
import cn.lgs.orbisops.domain.source.model.ProjectServiceCandidate;
import cn.lgs.orbisops.domain.source.service.ProjectServicePolicy;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectServicePolicyTest {

    private final ProjectServicePolicy policy = new ProjectServicePolicy();

    @Test
    void preservesHttpDefaultsAndFixedBuildProfiles() {
        ProjectServiceCandidate normalized = policy.normalize(new ProjectServiceCandidate(
                "project-1", "service-1", "", "repo-1", "", "maven_verify",
                "", "", "https://example.test/health",
                List.of("https://example.test/smoke", "https://example.test/smoke")));
        ProjectService service = new ProjectService(
                normalized.serviceId(), normalized.projectId(), normalized.name(), normalized.repositoryId(),
                normalized.modulePath(), BuildProfile.require(normalized.buildProfile()), normalized.artifactPath(),
                normalized.deploymentResourceId(), normalized.healthUrl(), normalized.smokeUrls(),
                "READY", "now", "now");
        ProjectServiceBuildCommand command = policy.buildCommand(service, Path.of("/tmp/repo"));

        assertEquals("service-1", normalized.name());
        assertEquals(".", normalized.modulePath());
        assertEquals("", normalized.artifactPath());
        assertEquals(List.of("https://example.test/smoke"), normalized.smokeUrls());
        assertEquals(List.of("mvn", "-q", "test", "package"), command.command());
    }

    @Test
    void rejectsArbitraryBuildProfilesPathsAndUrls() {
        assertEquals("不支持的构建配置：shell",
                assertThrows(IllegalArgumentException.class, () -> policy.normalize(new ProjectServiceCandidate(
                        "project-1", "service-1", "service", "repo-1", ".", "shell",
                        "", "", "", List.of()))).getMessage());
        assertThrows(IllegalArgumentException.class, () -> policy.normalize(new ProjectServiceCandidate(
                "project-1", "service-1", "service", "repo-1", "../escape", "MAKE_CI",
                "", "", "", List.of())));
        assertThrows(IllegalArgumentException.class, () -> policy.normalize(new ProjectServiceCandidate(
                "project-1", "service-1", "service", "repo-1", ".", "MAKE_CI",
                "", "", "file:///etc/passwd", List.of())));
    }
}
