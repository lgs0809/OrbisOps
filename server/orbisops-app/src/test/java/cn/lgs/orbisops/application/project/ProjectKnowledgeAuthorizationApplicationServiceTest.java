package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.application.knowledge.KnowledgeAuthorizationApplicationService;
import cn.lgs.orbisops.application.knowledge.KnowledgeWorkspaceCatalogPort;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeBaseCatalogEntry;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeBaseCatalogKey;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeScope;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeStatus;
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

class ProjectKnowledgeAuthorizationApplicationServiceTest {

    @Test
    void combinesLegacyProjectAndAuthorizedGlobalKnowledgeBases() {
        ProjectDefinitionApplicationService definitions =
                mock(ProjectDefinitionApplicationService.class);
        KnowledgeAuthorizationApplicationService authorizations =
                mock(KnowledgeAuthorizationApplicationService.class);
        KnowledgeWorkspaceCatalogPort catalog = mock(KnowledgeWorkspaceCatalogPort.class);
        ProjectDefinition project = mock(ProjectDefinition.class);
        KnowledgeBaseCatalogEntry projectEntry = entry(
                KnowledgeScope.PROJECT, "project-1", "project-kb", "项目知识库");
        KnowledgeBaseCatalogEntry globalEntry = entry(
                KnowledgeScope.GLOBAL, "", "global-kb", "全局知识库");
        when(project.knowledgeBaseId()).thenReturn("legacy-kb");
        when(definitions.findDefinition("project-1")).thenReturn(Optional.of(project));
        when(catalog.listEnabledProject("project-1"))
                .thenReturn(List.of(projectEntry));
        when(authorizations.enabledKnowledgeBaseIds("project-1"))
                .thenReturn(List.of("global-kb", "missing-global"));
        when(catalog.listEnabledGlobalByIds(List.of("global-kb", "missing-global")))
                .thenReturn(List.of(globalEntry));
        ProjectKnowledgeAuthorizationApplicationService service =
                new ProjectKnowledgeAuthorizationApplicationService(
                        definitions, authorizations, catalog);

        assertEquals(List.of("legacy-kb", "project-kb"), service.localIds("project-1"));
        assertEquals(List.of("global-kb"), service.globalIds("project-1"));
        assertEquals(List.of("legacy-kb", "project-kb", "global-kb"),
                service.enabledIds("project-1"));
        assertEquals("legacy-kb", service.defaultId("project-1"));
        assertEquals(List.of(projectEntry), service.projectEntries("project-1"));
        assertEquals(List.of(globalEntry), service.globalEntries("project-1"));
        assertEquals("LEGACY", service.scope("project-1", "legacy-kb"));
        assertEquals("PROJECT", service.scope("project-1", "project-kb"));
        assertEquals("GLOBAL", service.scope("project-1", "global-kb"));
        assertEquals("", service.scope("project-1", "missing"));
        assertTrue(service.allows("project-1", "project-kb"));
        assertTrue(service.allows("project-1", "global-kb"));
        assertFalse(service.allows("project-1", "missing"));
    }

    @Test
    void defaultsToEnabledProjectCatalogWhenLegacyDefaultIsBlank() {
        ProjectDefinitionApplicationService definitions = mock(ProjectDefinitionApplicationService.class);
        KnowledgeAuthorizationApplicationService authorizations = mock(KnowledgeAuthorizationApplicationService.class);
        KnowledgeWorkspaceCatalogPort catalog = mock(KnowledgeWorkspaceCatalogPort.class);
        ProjectDefinition project = mock(ProjectDefinition.class);
        KnowledgeBaseCatalogEntry projectEntry = entry(
                KnowledgeScope.PROJECT, "project-1", "project-kb", "项目知识库");
        when(project.knowledgeBaseId()).thenReturn("");
        when(definitions.findDefinition("project-1")).thenReturn(Optional.of(project));
        when(catalog.listEnabledProject("project-1")).thenReturn(List.of(projectEntry));
        when(authorizations.enabledKnowledgeBaseIds("project-1")).thenReturn(List.of());
        when(catalog.listEnabledGlobalByIds(List.of())).thenReturn(List.of());
        ProjectKnowledgeAuthorizationApplicationService service =
                new ProjectKnowledgeAuthorizationApplicationService(definitions, authorizations, catalog);

        assertEquals("project-kb", service.defaultId("project-1"));
    }

    @Test
    void defaultsToAuthorizedGlobalCatalogWhenNoLocalKnowledgeBaseExists() {
        ProjectDefinitionApplicationService definitions = mock(ProjectDefinitionApplicationService.class);
        KnowledgeAuthorizationApplicationService authorizations = mock(KnowledgeAuthorizationApplicationService.class);
        KnowledgeWorkspaceCatalogPort catalog = mock(KnowledgeWorkspaceCatalogPort.class);
        ProjectDefinition project = mock(ProjectDefinition.class);
        KnowledgeBaseCatalogEntry globalEntry = entry(
                KnowledgeScope.GLOBAL, "", "global-kb", "全局知识库");
        when(project.knowledgeBaseId()).thenReturn("");
        when(definitions.findDefinition("project-1")).thenReturn(Optional.of(project));
        when(catalog.listEnabledProject("project-1")).thenReturn(List.of());
        when(authorizations.enabledKnowledgeBaseIds("project-1")).thenReturn(List.of("global-kb"));
        when(catalog.listEnabledGlobalByIds(List.of("global-kb"))).thenReturn(List.of(globalEntry));
        ProjectKnowledgeAuthorizationApplicationService service =
                new ProjectKnowledgeAuthorizationApplicationService(definitions, authorizations, catalog);

        assertEquals("global-kb", service.defaultId("project-1"));
    }

    @Test
    void rejectsMissingProjectAndBlankIdentifiers() {
        ProjectDefinitionApplicationService definitions =
                mock(ProjectDefinitionApplicationService.class);
        KnowledgeAuthorizationApplicationService authorizations =
                mock(KnowledgeAuthorizationApplicationService.class);
        KnowledgeWorkspaceCatalogPort catalog = mock(KnowledgeWorkspaceCatalogPort.class);
        when(definitions.findDefinition("missing")).thenReturn(Optional.empty());
        ProjectKnowledgeAuthorizationApplicationService service =
                new ProjectKnowledgeAuthorizationApplicationService(
                        definitions, authorizations, catalog);

        assertEquals("PROJECT_ID_REQUIRED", assertThrows(
                IllegalArgumentException.class,
                () -> service.enabledIds(" ")).getMessage());
        assertEquals("项目不存在：missing", assertThrows(
                IllegalArgumentException.class,
                () -> service.enabledIds("missing")).getMessage());
        assertFalse(service.allows("", "kb-1"));
        assertFalse(service.allows("project-1", ""));
        assertEquals("", service.scope("", "kb-1"));
    }

    private KnowledgeBaseCatalogEntry entry(
            KnowledgeScope scope,
            String projectId,
            String kbId,
            String name) {
        return new KnowledgeBaseCatalogEntry(
                1L,
                new KnowledgeBaseCatalogKey(scope, projectId, kbId),
                name,
                "",
                KnowledgeStatus.ENABLED,
                0L,
                0L,
                "DB",
                "{}",
                "test",
                "2026-07-19T00:00:00",
                "2026-07-19T00:00:00");
    }
}
