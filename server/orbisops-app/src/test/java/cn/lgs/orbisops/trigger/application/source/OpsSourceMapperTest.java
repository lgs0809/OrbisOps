package cn.lgs.orbisops.trigger.application.source;

import cn.lgs.orbisops.api.dto.OpsDeploymentRevisionRequestDTO;
import cn.lgs.orbisops.api.dto.OpsProjectServiceRequestDTO;
import cn.lgs.orbisops.api.dto.OpsSourceRepositoryRequestDTO;
import cn.lgs.orbisops.domain.source.model.DeploymentRevision;
import cn.lgs.orbisops.domain.source.model.ProjectService;
import cn.lgs.orbisops.domain.source.model.SourceFile;
import cn.lgs.orbisops.domain.source.model.SourceMcpRuntimeSpec;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import cn.lgs.orbisops.domain.source.model.SourceSearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpsSourceMapperTest {

    private final OpsSourceMapper mapper = new OpsSourceMapper();

    @Test
    void mapsRequestsEntitiesAndLegacyDtos() {
        var repositoryCandidate = mapper.repositoryCandidate(OpsSourceRepositoryRequestDTO.builder()
                .projectId("project-1").repositoryId("repo-1").name("repo")
                .localPath("/tmp/repo").defaultRevision("HEAD").build());
        var deploymentCandidate = mapper.deploymentCandidate(OpsDeploymentRevisionRequestDTO.builder()
                .projectId("project-1").repositoryId("repo-1").environment("prod")
                .serviceName("service-1").revision("HEAD").imageRef("image:v1").build());
        var serviceCandidate = mapper.serviceCandidate(OpsProjectServiceRequestDTO.builder()
                .projectId("project-1").serviceId("service-1").repositoryId("repo-1")
                .modulePath(".").buildProfile("MAKE_CI").build());

        assertEquals("repo-1", repositoryCandidate.repositoryId());
        assertEquals("service-1", deploymentCandidate.serviceName());
        assertEquals("MAKE_CI", serviceCandidate.buildProfile());
        assertEquals("repo-1", mapper.repositoryView(repository()).getRepositoryId());
        assertEquals("project-1:prod:service-1", mapper.deploymentView(deployment()).getDeploymentId());
        assertEquals("service-1", mapper.serviceView(service()).getServiceId());
        assertEquals("content", mapper.fileView(new SourceFile("repo-1", sha(), "a.txt", 7, "content")).getContent());
        assertEquals(3, mapper.hitView(new SourceSearchHit("repo-1", sha(), "a.txt", 3, "text")).getLine());
    }

    @Test
    void mapsMcpRuntimeAndCapabilitiesWithoutLeakingDomainTypes() {
        var mcp = mapper.mcpView(new SourceMcpRuntimeSpec(
                "repo-1-readonly-git-mcp", "description", "stdio", "node", List.of("server.mjs"),
                Map.of("ROOT", "/tmp/repo"), 8, Map.of("*", "read_only"), List.of("git_read_file")));

        assertEquals("node", mcp.getCommand());
        assertEquals("read_only", mcp.getToolCapabilities().get("*"));
    }

    private SourceRepository repository() {
        return new SourceRepository(
                "repo-1", "repo-1-readonly-git-mcp", "project-1", "repo", "/tmp/repo",
                "HEAD", sha(), "READY", "admin", "now", "now");
    }

    private DeploymentRevision deployment() {
        return new DeploymentRevision(
                "project-1:prod:service-1", "project-1", "repo-1", "prod", "service-1",
                sha(), "image:v1", "admin", "now", "now");
    }

    private ProjectService service() {
        return new ProjectService(
                "service-1", "project-1", "service", "repo-1", ".",
                cn.lgs.orbisops.domain.source.model.BuildProfile.MAKE_CI,
                "", "", "", List.of(), "READY", "now", "now");
    }

    private String sha() {
        return "0123456789abcdef0123456789abcdef01234567";
    }
}
