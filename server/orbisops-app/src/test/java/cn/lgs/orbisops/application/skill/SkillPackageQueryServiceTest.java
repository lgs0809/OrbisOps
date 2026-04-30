package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillPackageRepository;
import cn.lgs.orbisops.domain.skill.model.SkillArtifact;
import cn.lgs.orbisops.domain.skill.model.SkillPackageKey;
import cn.lgs.orbisops.domain.skill.model.SkillPackageVersion;
import cn.lgs.orbisops.domain.skill.service.SkillPackageManifest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillPackageQueryServiceTest {

    @Test
    void currentIdentityExactHitUsesCurrentViewAndAddsStatusAtUse() {
        Fixture fixture = fixture(true);
        Map<String, Object> current = currentView("GLOBAL", "", "diagnosis", 2,
                "hash-2", "package-2", "PAUSED", "# Current\n");
        when(fixture.catalog().getGlobalEntry("diagnosis")).thenReturn(SkillCatalogSnapshot.fromView(current));
        SkillPackageKey key = new SkillPackageKey("GLOBAL", "", "diagnosis", 2);
        when(fixture.repository().findArtifacts(key)).thenReturn(List.of(artifact(
                "resources/query.yaml", "RESOURCE", "text/yaml; charset=utf-8", "query-hash", "query: slow\n")));

        Map<String, Object> result = fixture.service().getVersion(
                "", "diagnosis", 2, "hash-2", "package-2", "GLOBAL");

        assertEquals("# Current\n", result.get("content"));
        assertEquals("PAUSED", result.get("statusAtUse"));
        assertEquals(1, result.get("artifactCount"));
        verify(fixture.repository(), never()).findVersion(key);
    }

    @Test
    void historicalImmutableVersionMergesCurrentMetadataButKeepsHistoricalContent() {
        Fixture fixture = fixture(true);
        when(fixture.catalog().getGlobalEntry("diagnosis")).thenReturn(current(
                "GLOBAL", "", "diagnosis", 3, "hash-3", "package-3", "FROZEN", "# Current\n"));
        SkillPackageManifest.Descriptor descriptor = descriptor(
                "GLOBAL", "", "diagnosis", 2, "# Historical\n");
        SkillPackageVersion historical = version(
                "GLOBAL", "", "diagnosis", 2, "hash-2", descriptor, "# Historical\n");
        SkillPackageKey key = historical.key();
        when(fixture.repository().findVersion(key)).thenReturn(Optional.of(historical));
        when(fixture.repository().findArtifacts(key)).thenReturn(descriptor.artifacts().values().stream()
                .map(this::artifact).toList());

        Map<String, Object> result = fixture.service().getVersion(
                "", "diagnosis", 2, "hash-2", descriptor.packageHash(), "GLOBAL");

        assertEquals(2, result.get("version"));
        assertEquals(2, result.get("currentVersion"));
        assertEquals("hash-2", result.get("currentSkillHash"));
        assertEquals("# Historical\n", result.get("content"));
        assertEquals("FROZEN", result.get("statusAtUse"));
        assertEquals(descriptor.packageHash(), result.get("packageHash"));
    }

    @Test
    void historicalSkillHashMismatchIsRejected() {
        Fixture fixture = fixture(true);
        when(fixture.catalog().getGlobalEntry("diagnosis")).thenReturn(current(
                "GLOBAL", "", "diagnosis", 3, "hash-3", "package-3", "ACTIVE", "# Current\n"));
        SkillPackageManifest.Descriptor descriptor = descriptor(
                "GLOBAL", "", "diagnosis", 2, "# Historical\n");
        SkillPackageVersion historical = version(
                "GLOBAL", "", "diagnosis", 2, "hash-2", descriptor, "# Historical\n");
        when(fixture.repository().findVersion(historical.key())).thenReturn(Optional.of(historical));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> fixture.service().getVersion(
                        "", "diagnosis", 2, "forged-hash", descriptor.packageHash(), "GLOBAL"));

        assertTrue(error.getMessage().contains("diagnosis@2"));
    }

    @Test
    void historicalPackageHashMismatchIsRejected() {
        Fixture fixture = fixture(true);
        when(fixture.catalog().getGlobalEntry("diagnosis")).thenReturn(current(
                "GLOBAL", "", "diagnosis", 3, "hash-3", "package-3", "ACTIVE", "# Current\n"));
        SkillPackageManifest.Descriptor descriptor = descriptor(
                "GLOBAL", "", "diagnosis", 2, "# Historical\n");
        SkillPackageVersion historical = version(
                "GLOBAL", "", "diagnosis", 2, "hash-2", descriptor, "# Historical\n");
        when(fixture.repository().findVersion(historical.key())).thenReturn(Optional.of(historical));

        assertThrows(IllegalStateException.class, () -> fixture.service().getVersion(
                "", "diagnosis", 2, "hash-2", "forged-package", "GLOBAL"));
    }

    @Test
    void projectValidationFailsBeforeCatalogOrPackageRead() {
        Fixture fixture = fixture(true);
        doThrow(new IllegalArgumentException("项目不存在：missing"))
                .when(fixture.projects()).requireExisting("missing");

        assertThrows(IllegalArgumentException.class, () -> fixture.service().getVersion(
                "missing", "diagnosis", 1, "hash-1", "", "PROJECT"));

        verify(fixture.catalog(), never()).getProjectEntry("missing", "diagnosis");
        verify(fixture.repository(), never()).findVersion(new SkillPackageKey(
                "PROJECT", "missing", "diagnosis", 1));
    }

    @Test
    void unavailablePackageStoreRejectsHistoricalIdentityButAllowsCurrentEntrypoint() {
        Fixture fixture = fixture(false);
        Map<String, Object> current = currentView(
                "GLOBAL", "", "diagnosis", 3, "hash-3", "package-3", "ACTIVE", "# Current\n");
        current.put("artifactHashes", Map.of("SKILL.md", "entry-hash"));
        when(fixture.catalog().getGlobalEntry("diagnosis")).thenReturn(SkillCatalogSnapshot.fromView(current));

        assertThrows(IllegalStateException.class, () -> fixture.service().getVersion(
                "", "diagnosis", 2, "hash-2", "", "GLOBAL"));

        Map<String, Object> entrypoint = fixture.service().getArtifact(
                "", "diagnosis", 3, "hash-3", "package-3", "GLOBAL", "SKILL.md");
        assertEquals("# Current\n", entrypoint.get("content"));
        assertEquals("entry-hash", entrypoint.get("contentHash"));
    }

    @Test
    void exactArtifactReadPreservesContentAndMapsAllMetadata() {
        Fixture fixture = fixture(true);
        when(fixture.catalog().getGlobalEntry("diagnosis")).thenReturn(current(
                "GLOBAL", "", "diagnosis", 2, "hash-2", "package-2", "ACTIVE", "# Current\n"));
        String content = "  keep-leading-space\nkeep-trailing-space  \n";
        SkillArtifact artifact = artifact(
                "resources/example.txt", "RESOURCE", "text/plain; charset=utf-8", "content-hash", content);
        SkillPackageKey key = new SkillPackageKey("GLOBAL", "", "diagnosis", 2);
        when(fixture.repository().findArtifact(key, "resources/example.txt")).thenReturn(Optional.of(artifact));

        Map<String, Object> result = fixture.service().getArtifact(
                "", "diagnosis", 2, "hash-2", "package-2", "GLOBAL", "resources/example.txt");

        assertEquals("resources/example.txt", result.get("path"));
        assertEquals("RESOURCE", result.get("role"));
        assertEquals("text/plain; charset=utf-8", result.get("mediaType"));
        assertEquals("UTF8", result.get("encoding"));
        assertEquals("content-hash", result.get("contentHash"));
        assertEquals((long) content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, result.get("sizeBytes"));
        assertEquals(content, result.get("content"));
    }

    @Test
    void missingNonEntrypointArtifactFailsClosed() {
        Fixture fixture = fixture(true);
        when(fixture.catalog().getGlobalEntry("diagnosis")).thenReturn(current(
                "GLOBAL", "", "diagnosis", 2, "hash-2", "package-2", "ACTIVE", "# Current\n"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> fixture.service().getArtifact(
                        "", "diagnosis", 2, "hash-2", "package-2", "GLOBAL", "resources/missing.json"));

        assertTrue(error.getMessage().contains("resources/missing.json"));
    }

    @Test
    void listArtifactsFallsBackOnlyToEntrypointWhenNoStoredArtifactsExist() {
        Fixture fixture = fixture(true);
        Map<String, Object> current = currentView(
                "GLOBAL", "", "diagnosis", 2, "hash-2", "package-2", "ACTIVE", "# Current\n");
        current.put("artifactHashes", Map.of("SKILL.md", "entry-hash"));
        when(fixture.catalog().getGlobalEntry("diagnosis")).thenReturn(SkillCatalogSnapshot.fromView(current));
        SkillPackageKey key = new SkillPackageKey("GLOBAL", "", "diagnosis", 2);
        when(fixture.repository().findArtifacts(key)).thenReturn(List.of());

        List<Map<String, Object>> artifacts = fixture.service().listArtifacts(
                "", "diagnosis", 2, "hash-2", "package-2", "GLOBAL");

        assertEquals(1, artifacts.size());
        assertEquals("SKILL.md", artifacts.get(0).get("path"));
        assertEquals("ENTRYPOINT", artifacts.get(0).get("role"));
        assertEquals("entry-hash", artifacts.get(0).get("contentHash"));
    }

    @Test
    void listVersionsMapsImmutablePackageMetadataAndValidatesProjectScope() {
        Fixture fixture = fixture(true);
        SkillPackageManifest.Descriptor descriptor = descriptor(
                "PROJECT", "project-1", "diagnosis", 4, "# Version 4\n");
        SkillPackageVersion stored = version(
                "PROJECT", "project-1", "diagnosis", 4, "hash-4", descriptor, "# Version 4\n");
        when(fixture.repository().findVersions("PROJECT", "project-1", "diagnosis"))
                .thenReturn(List.of(stored));

        List<Map<String, Object>> result = fixture.service().listProjectVersions("project-1", "diagnosis");

        verify(fixture.projects()).requireExisting("project-1");
        assertEquals(1, result.size());
        Map<String, Object> version = result.get(0);
        assertEquals(4, version.get("version"));
        assertEquals("hash-4", version.get("skillHash"));
        assertEquals(descriptor.packageHash(), version.get("packageHash"));
        assertEquals(descriptor.entrypoint(), version.get("entrypoint"));
        assertEquals(descriptor.artifactCount(), version.get("artifactCount"));
        assertEquals(descriptor.packageSize(), version.get("packageSize"));
        assertInstanceOf(Map.class, version.get("artifactHashes"));
        assertEquals(64, String.valueOf(version.get("manifestHash")).length());
    }

    @Test
    void emptyExpectedPackageHashKeepsLegacyCompatibility() {
        Fixture fixture = fixture(true);
        when(fixture.catalog().getGlobalEntry("diagnosis")).thenReturn(current(
                "GLOBAL", "", "diagnosis", 2, "hash-2", "package-2", "ACTIVE", "# Current\n"));

        Map<String, Object> result = fixture.service().getVersion(
                "", "diagnosis", 2, "hash-2", "", "GLOBAL");

        assertEquals(2, result.get("currentVersion"));
    }

    private Fixture fixture(boolean repositoryAvailable) {
        SkillCatalogPort catalog = mock(SkillCatalogPort.class);
        ISkillPackageRepository repository = mock(ISkillPackageRepository.class);
        SkillProjectValidationPort projects = mock(SkillProjectValidationPort.class);
        when(repository.available()).thenReturn(repositoryAvailable);
        when(repository.findArtifacts(any(SkillPackageKey.class))).thenReturn(List.of());
        return new Fixture(catalog, repository, projects,
                new SkillPackageQueryService(catalog, repository, projects));
    }

    private SkillCatalogSnapshot current(String scope,
                                         String projectId,
                                         String skillId,
                                         int version,
                                         String skillHash,
                                         String packageHash,
                                         String status,
                                         String content) {
        return SkillCatalogSnapshot.fromView(currentView(
                scope, projectId, skillId, version, skillHash, packageHash, status, content));
    }

    private Map<String, Object> currentView(String scope,
                                            String projectId,
                                            String skillId,
                                            int version,
                                            String skillHash,
                                            String packageHash,
                                            String status,
                                            String content) {
        Map<String, Object> current = new LinkedHashMap<>();
        current.put("skillId", skillId);
        current.put("name", "Diagnosis");
        current.put("description", "Collect evidence");
        current.put("scope", scope);
        current.put("projectId", projectId);
        current.put("version", version);
        current.put("currentVersion", version);
        current.put("skillHash", skillHash);
        current.put("currentSkillHash", skillHash);
        current.put("packageHash", packageHash);
        current.put("currentPackageHash", packageHash);
        current.put("status", status);
        current.put("updateMode", "AUTO");
        current.put("content", content);
        return current;
    }

    private SkillPackageManifest.Descriptor descriptor(String scope,
                                                        String projectId,
                                                        String skillId,
                                                        int version,
                                                        String content) {
        return SkillPackageManifest.packageOf(
                scope, projectId, skillId, "Diagnosis", "Collect evidence", version, content,
                List.of(Map.of(
                        "path", "resources/query.yaml",
                        "role", "RESOURCE",
                        "mediaType", "text/yaml; charset=utf-8",
                        "encoding", "UTF8",
                        "content", "query: slow\n")),
                List.of(), List.of(), SkillPackageManifest.Limits.defaults());
    }

    private SkillPackageVersion version(String scope,
                                        String projectId,
                                        String skillId,
                                        int version,
                                        String skillHash,
                                        SkillPackageManifest.Descriptor descriptor,
                                        String content) {
        return new SkillPackageVersion(
                42L,
                new SkillPackageKey(scope, projectId, skillId, version),
                skillHash,
                Math.max(0, version - 1),
                version > 1 ? "hash-" + (version - 1) : "",
                "run-1",
                "session-1",
                "job-1",
                "MANUAL_UPDATE",
                content,
                "MANUAL",
                "alice",
                "update package",
                descriptor.packageHash(),
                descriptor.manifestJson(),
                com.alibaba.fastjson.JSON.toJSONString(descriptor.artifactHashes()),
                descriptor.entrypoint(),
                descriptor.artifactCount(),
                descriptor.packageSize(),
                Instant.parse("2026-07-18T08:00:00Z"));
    }

    private SkillArtifact artifact(SkillPackageManifest.ArtifactContent artifact) {
        return new SkillArtifact(
                artifact.path(), artifact.role(), artifact.mediaType(), artifact.encoding(),
                artifact.contentHash(), artifact.sizeBytes(), artifact.content());
    }

    private SkillArtifact artifact(String path,
                                   String role,
                                   String mediaType,
                                   String contentHash,
                                   String content) {
        return new SkillArtifact(
                path, role, mediaType, "UTF8", contentHash,
                content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, content);
    }

    private record Fixture(SkillCatalogPort catalog,
                           ISkillPackageRepository repository,
                           SkillProjectValidationPort projects,
                           SkillPackageQueryService service) {
    }
}
