package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.application.skill.SkillCatalogQueryService;
import cn.lgs.orbisops.domain.project.model.ProjectDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectSkillAuthorizationApplicationServiceTest {

    @Test
    void classifiesProjectAndEnabledGlobalSkillsUsingCatalogScope() {
        ProjectDefinitionApplicationService definitions =
                mock(ProjectDefinitionApplicationService.class);
        ProjectDefinition project = mock(ProjectDefinition.class);
        SkillCatalogQueryService catalog = mock(SkillCatalogQueryService.class);
        when(project.skillIds()).thenReturn(List.of(
                "project-configured",
                "global-enabled",
                "project-shadow",
                "unknown-configured"));
        when(definitions.findDefinition("project-1")).thenReturn(Optional.of(project));
        when(catalog.projectCatalogSkillIds("project-1")).thenReturn(List.of(
                "project-catalog",
                "project-shadow"));
        when(catalog.globalCatalogSkillIds()).thenReturn(List.of(
                "global-enabled",
                "project-shadow"));
        ProjectSkillAuthorizationApplicationService service =
                new ProjectSkillAuthorizationApplicationService(
                        definitions, catalog);

        assertEquals(List.of(
                        "project-configured",
                        "project-shadow",
                        "unknown-configured",
                        "project-catalog"),
                service.localIds("project-1"));
        assertEquals(List.of("global-enabled"),
                service.globalIds("project-1"));
        assertEquals(List.of(
                        "project-configured",
                        "project-shadow",
                        "unknown-configured",
                        "project-catalog",
                        "global-enabled"),
                service.enabledIds("project-1"));
        assertTrue(service.allows("project-1", "project-catalog"));
        assertTrue(service.allows("project-1", "global-enabled"));
        assertFalse(service.allows("project-1", "missing"));
    }

    @Test
    void rejectsMissingProjectAndBlankIdentifiers() {
        ProjectDefinitionApplicationService definitions =
                mock(ProjectDefinitionApplicationService.class);
        SkillCatalogQueryService catalog = mock(SkillCatalogQueryService.class);
        when(definitions.findDefinition("missing")).thenReturn(Optional.empty());
        ProjectSkillAuthorizationApplicationService service =
                new ProjectSkillAuthorizationApplicationService(
                        definitions, catalog);

        assertEquals("PROJECT_ID_REQUIRED", assertThrows(
                IllegalArgumentException.class,
                () -> service.enabledIds(" ")).getMessage());
        assertEquals("项目不存在：missing", assertThrows(
                IllegalArgumentException.class,
                () -> service.enabledIds("missing")).getMessage());
        assertFalse(service.allows("", "skill-1"));
        assertFalse(service.allows("project-1", ""));
    }
}
