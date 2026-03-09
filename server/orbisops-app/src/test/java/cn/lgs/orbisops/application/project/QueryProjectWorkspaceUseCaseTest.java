package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryProjectWorkspaceUseCaseTest {

    @Test
    void projectToolsReadFromTypedCatalogAfterProjectValidation() {
        ProjectWorkspacePort workspacePort = mock(ProjectWorkspacePort.class);
        ProjectMemberApplicationService memberService =
                mock(ProjectMemberApplicationService.class);
        ProjectMcpCatalogApplicationService catalogService =
                mock(ProjectMcpCatalogApplicationService.class);
        ProjectMcpDefinition definition = mock(ProjectMcpDefinition.class);
        QueryProjectWorkspaceUseCase useCase =
                new QueryProjectWorkspaceUseCase(
                        workspacePort, memberService, catalogService);
        when(workspacePort.detail("project-1"))
                .thenReturn(Map.of("projectId", "project-1"));
        when(catalogService.list("project-1"))
                .thenReturn(List.of(definition));
        when(catalogService.view(definition))
                .thenReturn(Map.of(
                        "projectId", "project-1",
                        "toolId", "tool-1"));

        List<Map<String, Object>> tools = useCase.projectTools("project-1");

        assertEquals(List.of(Map.of(
                "projectId", "project-1",
                "toolId", "tool-1")), tools);
        verify(workspacePort).detail("project-1");
        verify(catalogService).list("project-1");
    }
}
