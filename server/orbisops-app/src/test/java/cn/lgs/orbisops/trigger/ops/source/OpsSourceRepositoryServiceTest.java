package cn.lgs.orbisops.trigger.ops.source;

import cn.lgs.orbisops.api.dto.OpsSourceRepositoryRequestDTO;
import cn.lgs.orbisops.application.source.SourceRepositoryApplicationService;
import cn.lgs.orbisops.domain.source.model.SourceMcpRuntimeSpec;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import cn.lgs.orbisops.domain.source.model.SourceRepositoryCandidate;
import cn.lgs.orbisops.domain.source.model.SourceRepositoryCapabilities;
import cn.lgs.orbisops.trigger.application.source.OpsSourceMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsSourceRepositoryServiceTest {

    @Test
    void compatibilityAclMapsDtosAndDelegatesToTypedApplication() {
        SourceRepositoryApplicationService sources = mock(SourceRepositoryApplicationService.class);
        SourceRepository repository = repository();
        when(sources.register(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("admin")))
                .thenReturn(repository);
        when(sources.list("project-1")).thenReturn(List.of(repository));
        OpsSourceRepositoryService service = new OpsSourceRepositoryService(sources, new OpsSourceMapper());
        OpsSourceRepositoryRequestDTO request = OpsSourceRepositoryRequestDTO.builder()
                .projectId("project-1").repositoryId("repo-1").name("repo")
                .localPath("/tmp/repo").defaultRevision("HEAD").build();

        assertEquals("repo-1", service.registerRepository(request, "admin").getRepositoryId());
        assertEquals(1, service.listRepositories("project-1").size());
        ArgumentCaptor<SourceRepositoryCandidate> candidate = ArgumentCaptor.forClass(SourceRepositoryCandidate.class);
        verify(sources).register(candidate.capture(), org.mockito.ArgumentMatchers.eq("admin"));
        assertEquals("/tmp/repo", candidate.getValue().localPath());
    }

    @Test
    void compatibilityAclMapsCapabilitiesAndMcpRuntime() {
        SourceRepositoryApplicationService sources = mock(SourceRepositoryApplicationService.class);
        when(sources.capabilities()).thenReturn(new SourceRepositoryCapabilities(
                false, "LOCAL_GIT_READ_ONLY", List.of("READ_FILE_AT_COMMIT"), false, false));
        when(sources.resolveMcp("project-1", "repo-1-readonly-git-mcp"))
                .thenReturn(Optional.of(new SourceMcpRuntimeSpec(
                        "repo-1-readonly-git-mcp", "description", "stdio", "node", List.of("server.mjs"),
                        Map.of(), 8, Map.of("*", "read_only"), List.of("git_read_file"))));
        OpsSourceRepositoryService service = new OpsSourceRepositoryService(sources, new OpsSourceMapper());

        assertFalse((Boolean) service.capabilities().get("enabled"));
        assertEquals("read_only", service.resolveMcpServer("project-1", "repo-1-readonly-git-mcp")
                .orElseThrow().getToolCapabilities().get("*"));
    }

    private SourceRepository repository() {
        return new SourceRepository(
                "repo-1", "repo-1-readonly-git-mcp", "project-1", "repo", "/tmp/repo", "HEAD",
                "0123456789abcdef0123456789abcdef01234567", "READY", "admin", "now", "now");
    }
}
