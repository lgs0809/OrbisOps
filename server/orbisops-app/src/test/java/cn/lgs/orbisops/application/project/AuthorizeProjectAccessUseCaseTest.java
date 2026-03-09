package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.model.ProjectAction;
import cn.lgs.orbisops.domain.project.model.ProjectRole;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorizeProjectAccessUseCaseTest {

    @Test
    void filtersTypedCatalogUsingOwnerAndMemberFacts() {
        ProjectAccessPort port = mock(ProjectAccessPort.class);
        ProjectMemberApplicationService members = mock(ProjectMemberApplicationService.class);
        ProjectCatalogEntry owned = project("owned", "alice");
        ProjectCatalogEntry member = project("member", "bob");
        ProjectCatalogEntry forbidden = project("forbidden", "carol");
        when(port.publicCatalog()).thenReturn(List.of(owned, member, forbidden));
        when(port.owner("owned", "alice", "u-1")).thenReturn(true);
        when(members.enabledProjectIds("alice", "u-1")).thenReturn(Set.of("member"));
        AuthorizeProjectAccessUseCase useCase = new AuthorizeProjectAccessUseCase(port, members);

        List<ProjectCatalogEntry> result = useCase.catalog("alice", "u-1", false);

        assertEquals(List.of("owned", "member"), result.stream()
                .map(ProjectCatalogEntry::projectId)
                .toList());
    }

    @Test
    void resolvesTypedRoleAndRequiresDomainActionPolicy() {
        ProjectAccessPort port = mock(ProjectAccessPort.class);
        ProjectMemberApplicationService members = mock(ProjectMemberApplicationService.class);
        when(port.exists("project-1")).thenReturn(true);
        when(port.owner("project-1", "alice", "u-1")).thenReturn(false);
        when(members.role("project-1", "alice", "u-1")).thenReturn(ProjectRole.MAINTAINER);
        AuthorizeProjectAccessUseCase useCase = new AuthorizeProjectAccessUseCase(port, members);

        assertEquals(ProjectRole.MAINTAINER,
                useCase.role("project-1", "alice", "u-1", false));
        useCase.requireAction(
                "project-1", "alice", "u-1", false, ProjectAction.PREPARE_CHANGE);
        verify(members, times(2)).role("project-1", "alice", "u-1");
    }

    @Test
    void failsClosedForMissingOrUnauthorizedProject() {
        ProjectAccessPort port = mock(ProjectAccessPort.class);
        ProjectMemberApplicationService members = mock(ProjectMemberApplicationService.class);
        when(port.exists("missing")).thenReturn(false);
        AuthorizeProjectAccessUseCase useCase = new AuthorizeProjectAccessUseCase(port, members);

        assertFalse(useCase.canAccess("missing", "alice", "u-1", false));
        assertEquals("PROJECT_ACCESS_FORBIDDEN:missing", assertThrows(
                SecurityException.class,
                () -> useCase.requireAccess("missing", "alice", "u-1", false)).getMessage());
        assertFalse(useCase.canAccess("missing", "", "", true));
    }

    private ProjectCatalogEntry project(String projectId, String owner) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 31, 15, 40);
        return new ProjectCatalogEntry(
                projectId,
                projectId,
                "",
                owner,
                List.of("prod"),
                "",
                "",
                List.of(),
                List.of(),
                true,
                now,
                now);
    }
}
