package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillCatalogRepository;
import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillPackageRepository;
import cn.lgs.orbisops.domain.skill.model.SkillArtifact;
import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;
import cn.lgs.orbisops.domain.skill.model.SkillPackageKey;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillCatalogReadServiceTest {

    @Test
    void databaseCatalogOverridesSameFileSkillAndKeepsFileFallback() {
        ISkillCatalogRepository catalog = mock(ISkillCatalogRepository.class);
        ISkillPackageRepository packages = mock(ISkillPackageRepository.class);
        SkillFileSourcePort files = mock(SkillFileSourcePort.class);
        SkillProjectValidationPort projects = mock(SkillProjectValidationPort.class);
        when(catalog.available()).thenReturn(true);
        when(catalog.findAll("GLOBAL", "", false)).thenReturn(List.of(entry(
                1L, "diagnosis", "", "GLOBAL", "DB Diagnosis", "ACTIVE")));
        when(files.findAll()).thenReturn(List.of(
                file("diagnosis", "GLOBAL", ""),
                file("slow-sql", "GLOBAL", "")));
        SkillCatalogReadService service = new SkillCatalogReadService(catalog, packages, files, projects);

        List<Map<String, Object>> result = service.listGlobalEntries().stream()
                .map(SkillCatalogSnapshot::view)
                .toList();

        assertEquals(List.of("diagnosis", "slow-sql"), result.stream().map(item -> item.get("skillId")).toList());
        assertEquals("DB", result.get(0).get("sourceType"));
        assertEquals("FILE", result.get(1).get("sourceType"));
    }

    @Test
    void projectListValidatesProjectAndDoesNotLeakOtherProjectFiles() {
        ISkillCatalogRepository catalog = mock(ISkillCatalogRepository.class);
        ISkillPackageRepository packages = mock(ISkillPackageRepository.class);
        SkillFileSourcePort files = mock(SkillFileSourcePort.class);
        SkillProjectValidationPort projects = mock(SkillProjectValidationPort.class);
        when(catalog.available()).thenReturn(false);
        when(files.findAll()).thenReturn(List.of(
                file("project-one", "PROJECT", "p1"),
                file("project-two", "PROJECT", "p2"),
                file("global", "GLOBAL", "")));
        SkillCatalogReadService service = new SkillCatalogReadService(catalog, packages, files, projects);

        List<Map<String, Object>> result = service.listProjectEntries("p1").stream()
                .map(SkillCatalogSnapshot::view)
                .toList();

        verify(projects).requireExisting("p1");
        assertEquals(List.of("project-one"), result.stream().map(item -> item.get("skillId")).toList());
    }

    @Test
    void currentDatabaseSkillIncludesExactStoredArtifactMetadata() {
        ISkillCatalogRepository catalog = mock(ISkillCatalogRepository.class);
        ISkillPackageRepository packages = mock(ISkillPackageRepository.class);
        SkillFileSourcePort files = mock(SkillFileSourcePort.class);
        SkillProjectValidationPort projects = mock(SkillProjectValidationPort.class);
        SkillCatalogEntry entry = entry(7L, "diagnosis", "p1", "PROJECT", "Diagnosis", "ACTIVE");
        when(catalog.available()).thenReturn(true);
        when(catalog.find("PROJECT", "p1", "diagnosis", true)).thenReturn(Optional.of(entry));
        when(packages.available()).thenReturn(true);
        when(packages.findArtifacts(new SkillPackageKey("PROJECT", "p1", "diagnosis", 1))).thenReturn(List.of(
                new SkillArtifact("SKILL.md", "ENTRYPOINT", "text/markdown; charset=utf-8", "UTF8",
                        "entry-hash", 12L, "# Diagnosis"),
                new SkillArtifact("resources/query.yaml", "RESOURCE", "text/yaml; charset=utf-8", "UTF8",
                        "query-hash", 9L, "query: x")));
        SkillCatalogReadService service = new SkillCatalogReadService(catalog, packages, files, projects);

        Map<String, Object> result = service.getProjectEntry("p1", "diagnosis").view();

        verify(projects).requireExisting("p1");
        assertEquals(2, result.get("artifactCount"));
        assertEquals(21L, result.get("packageSize"));
        List<?> artifacts = (List<?>) result.get("artifacts");
        assertEquals("resources/query.yaml", ((Map<?, ?>) artifacts.get(1)).get("path"));
        assertTrue(!((Map<?, ?>) artifacts.get(1)).containsKey("content"));
    }

    @Test
    void legacyDatabaseSkillRebuildsEntrypointMetadataWhenNoArtifactRowsExist() {
        ISkillCatalogRepository catalog = mock(ISkillCatalogRepository.class);
        ISkillPackageRepository packages = mock(ISkillPackageRepository.class);
        SkillFileSourcePort files = mock(SkillFileSourcePort.class);
        SkillProjectValidationPort projects = mock(SkillProjectValidationPort.class);
        SkillCatalogEntry entry = entry(1L, "slow-sql", "", "GLOBAL", "Slow SQL", "ENABLED");
        when(catalog.available()).thenReturn(true);
        when(catalog.find("GLOBAL", "", "slow-sql", true)).thenReturn(Optional.of(entry));
        when(packages.available()).thenReturn(true);
        when(packages.findArtifacts(entry.key())).thenReturn(List.of());
        SkillCatalogReadService service = new SkillCatalogReadService(catalog, packages, files, projects);

        Map<String, Object> result = service.getGlobalEntry("slow-sql").view();

        assertEquals(1, result.get("artifactCount"));
        assertEquals("SKILL.md", ((Map<?, ?>) ((List<?>) result.get("artifacts")).get(0)).get("path"));
    }

    @Test
    void databaseCatalogFailsClosedWhenPackageStoreIsUnavailable() {
        ISkillCatalogRepository catalog = mock(ISkillCatalogRepository.class);
        ISkillPackageRepository packages = mock(ISkillPackageRepository.class);
        SkillFileSourcePort files = mock(SkillFileSourcePort.class);
        SkillProjectValidationPort projects = mock(SkillProjectValidationPort.class);
        SkillCatalogEntry entry = entry(1L, "slow-sql", "", "GLOBAL", "Slow SQL", "ENABLED");
        when(catalog.available()).thenReturn(true);
        when(catalog.find("GLOBAL", "", "slow-sql", true)).thenReturn(Optional.of(entry));
        when(packages.available()).thenReturn(false);
        SkillCatalogReadService service = new SkillCatalogReadService(catalog, packages, files, projects);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.getGlobalEntry("slow-sql"));

        assertEquals("SKILL_PACKAGE_STORE_UNAVAILABLE", error.getMessage());
    }

    @Test
    void projectCatalogIdsAreActiveOnlyAndStableByDatabaseId() {
        ISkillCatalogRepository catalog = mock(ISkillCatalogRepository.class);
        ISkillPackageRepository packages = mock(ISkillPackageRepository.class);
        SkillFileSourcePort files = mock(SkillFileSourcePort.class);
        SkillProjectValidationPort projects = mock(SkillProjectValidationPort.class);
        when(catalog.available()).thenReturn(true);
        when(catalog.findAll("PROJECT", "p1", false)).thenReturn(List.of(
                entry(9L, "late", "p1", "PROJECT", "Late", "ENABLED"),
                entry(2L, "disabled", "p1", "PROJECT", "Disabled", "DISABLED"),
                entry(3L, "early", "p1", "PROJECT", "Early", "ACTIVE")));
        when(catalog.findAll("GLOBAL", "", false)).thenReturn(List.of(
                entry(8L, "global-late", "", "GLOBAL", "Global Late", "ACTIVE"),
                entry(1L, "global-disabled", "", "GLOBAL", "Global Disabled", "DISABLED"),
                entry(4L, "global-early", "", "GLOBAL", "Global Early", "ENABLED")));
        SkillCatalogReadService service = new SkillCatalogReadService(catalog, packages, files, projects);

        assertEquals(List.of("early", "late"), service.projectCatalogSkillIds("p1"));
        assertEquals(List.of("global-early", "global-late"), service.globalCatalogSkillIds());
    }

    private SkillFileDefinition file(String skillId, String scope, String projectId) {
        return new SkillFileDefinition(skillId, "/skills/" + skillId,
                Map.of("name", skillId, "description", skillId, "scope", scope, "projectId", projectId),
                "# " + skillId, "---\nname: " + skillId + "\n---\n\n# " + skillId + "\n", "<skill/>");
    }

    private SkillCatalogEntry entry(long id,
                                    String skillId,
                                    String projectId,
                                    String scope,
                                    String name,
                                    String status) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 18, 12, 0);
        return new SkillCatalogEntry(
                id, skillId, projectId, name, scope, "", name + " description", "# " + name,
                1, status, "system", now, now, "MANUAL", "AUTO", true, true,
                null, "", "", null, "hash-" + skillId, 1, "hash-" + skillId, 1,
                "", "", "");
    }
}
