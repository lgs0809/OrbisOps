package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.application.project.ProjectCatalogEntry;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.domain.project.model.ProjectDefinition;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsProjectAccessAdapterTest {

    @Test
    void delegatesAccessFactsToProjectDefinitionBoundary() {
        ProjectDefinitionApplicationService definitions =
                mock(ProjectDefinitionApplicationService.class);
        when(definitions.exists("project-1")).thenReturn(true);
        when(definitions.owner("project-1", "alice", "u1")).thenReturn(true);
        when(definitions.owner("project-1", "bob", "u2")).thenReturn(false);
        ProjectDefinition project = project();
        when(definitions.listEnabledDefinitions()).thenReturn(List.of(project));
        OpsProjectAccessAdapter adapter = new OpsProjectAccessAdapter(definitions);

        assertTrue(adapter.exists("project-1"));
        assertTrue(adapter.owner("project-1", "alice", "u1"));
        assertFalse(adapter.owner("project-1", "bob", "u2"));
        assertEquals(List.of(ProjectCatalogEntry.from(project)), adapter.publicCatalog());
        verify(definitions).listEnabledDefinitions();
    }

    private ProjectDefinition project() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 31, 17, 30);
        return new ProjectDefinition(
                "project-1",
                "Orders",
                "",
                "alice",
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
