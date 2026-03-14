package cn.lgs.orbisops.trigger.ops.source;

import cn.lgs.orbisops.api.dto.OpsProjectServiceRequestDTO;
import cn.lgs.orbisops.application.source.ProjectServiceApplicationService;
import cn.lgs.orbisops.domain.source.model.BuildProfile;
import cn.lgs.orbisops.domain.source.model.ProjectService;
import cn.lgs.orbisops.domain.source.model.ProjectServiceBuildCommand;
import cn.lgs.orbisops.domain.source.model.ProjectServiceCandidate;
import cn.lgs.orbisops.domain.source.model.ProjectServiceCapabilities;
import cn.lgs.orbisops.trigger.application.source.OpsSourceMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsProjectServiceCatalogServiceTest {

    @Test
    void compatibilityAclMapsActorAndFixedBuildCommand() {
        ProjectServiceApplicationService application = mock(ProjectServiceApplicationService.class);
        ProjectService service = service();
        when(application.upsert(any(), eq("admin"))).thenReturn(service);
        when(application.buildCommand(service, Path.of("/tmp/repo")))
                .thenReturn(new ProjectServiceBuildCommand(
                        Path.of("/tmp/repo/demo-project"), List.of("mvn", "-q", "test", "package")));
        OpsProjectServiceCatalogService facade =
                new OpsProjectServiceCatalogService(application, new OpsSourceMapper());
        OpsProjectServiceRequestDTO request = OpsProjectServiceRequestDTO.builder()
                .projectId("demo-project").serviceId("order-service").repositoryId("demo-project-source")
                .modulePath("demo-project").buildProfile("MAVEN_VERIFY").build();

        var saved = facade.upsert(request, "admin");
        var command = facade.buildCommand(saved, Path.of("/tmp/repo"));

        assertEquals(List.of("mvn", "-q", "test", "package"), command.command());
        ArgumentCaptor<ProjectServiceCandidate> candidate = ArgumentCaptor.forClass(ProjectServiceCandidate.class);
        verify(application).upsert(candidate.capture(), eq("admin"));
        assertEquals("demo-project", candidate.getValue().modulePath());
    }

    @Test
    void compatibilityAclMapsCapabilities() {
        ProjectServiceApplicationService application = mock(ProjectServiceApplicationService.class);
        when(application.capabilities()).thenReturn(new ProjectServiceCapabilities(
                true, List.of("MAVEN_VERIFY", "NPM_TEST_BUILD", "MAKE_CI"), false, 3));
        OpsProjectServiceCatalogService facade =
                new OpsProjectServiceCatalogService(application, new OpsSourceMapper());

        assertEquals(3, facade.capabilities().get("serviceCount"));
        assertEquals(false, facade.capabilities().get("arbitraryShellAllowed"));
    }

    private ProjectService service() {
        return new ProjectService(
                "order-service", "demo-project", "Demo Project", "demo-project-source", "demo-project",
                BuildProfile.MAVEN_VERIFY, "demo-project/target/app.jar", "", "", List.of(),
                "READY", "now", "now");
    }
}
