package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.skill.SkillCanaryApplicationService;
import cn.lgs.orbisops.application.skill.SkillCanaryContextApplicationService;
import cn.lgs.orbisops.application.skill.SkillCanarySettings;
import cn.lgs.orbisops.application.skill.SkillCatalogPort;
import cn.lgs.orbisops.application.skill.SkillCatalogQueryService;
import cn.lgs.orbisops.application.skill.SkillCatalogSnapshot;
import cn.lgs.orbisops.application.skill.SkillPackageQueryService;
import cn.lgs.orbisops.application.skill.SkillReleasePackageAssembler;
import cn.lgs.orbisops.application.skill.SkillReleasePort;
import cn.lgs.orbisops.domain.skill.model.SkillFrozenCandidateSnapshot;
import cn.lgs.orbisops.domain.skill.service.SkillCanarySelectionPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsSkillReleaseServicePackageTest {

    @Test
    void evolutionPatchPreservesUnchangedPackageFilesAndReplacesSamePath() {
        SkillCatalogPort catalog = mock(SkillCatalogPort.class);
        SkillPackageQueryService packageQuery = mock(SkillPackageQueryService.class);
        SkillCatalogQueryService query = new SkillCatalogQueryService(
                catalog,
                packageQuery);
        SkillReleasePackageAssembler assembler =
                new SkillReleasePackageAssembler(query);
        when(catalog.getProjectEntry("project-a", "diagnosis"))
                .thenReturn(snapshot(3, "skill-hash-3", "package-hash-3"));
        when(packageQuery.listArtifacts(
                "project-a",
                "diagnosis",
                3,
                "skill-hash-3",
                "package-hash-3",
                "PROJECT")).thenReturn(List.of(
                Map.of(
                        "path", "SKILL.md",
                        "role", "ENTRYPOINT",
                        "encoding", "UTF8",
                        "content", "# Entry"),
                Map.of(
                        "path", "assets/logo.png",
                        "role", "ASSET",
                        "encoding", "BASE64",
                        "content", "AA=="),
                Map.of(
                        "path", "resources/method.json",
                        "role", "RESOURCE",
                        "encoding", "UTF8",
                        "content", "old")));

        List<Map<String, Object>> merged = assembler.mergeBaseArtifacts(
                "project-a",
                "diagnosis",
                3,
                "skill-hash-3",
                List.of(
                        Map.of(
                                "path", "resources/method.json",
                                "role", "RESOURCE",
                                "content", "new"),
                        Map.of(
                                "path", "scripts/check.sh",
                                "role", "SCRIPT",
                                "content", "echo ok\n")));

        assertEquals(3, merged.size());
        assertTrue(merged.stream().anyMatch(item ->
                "assets/logo.png".equals(item.get("path"))
                        && "AA==".equals(item.get("content"))));
        assertTrue(merged.stream().anyMatch(item ->
                "resources/method.json".equals(item.get("path"))
                        && "new".equals(item.get("content"))));
        assertTrue(merged.stream().anyMatch(item ->
                "scripts/check.sh".equals(item.get("path"))));
    }

    @Test
    void frozenCanaryRefLoadsExactCandidateEvenAfterReleaseStateChanges() {
        SkillReleasePort releasePort = mock(SkillReleasePort.class);
        when(releasePort.findFrozenCandidate(
                "candidate-1",
                "project-a",
                "candidate-hash-1",
                "release-1",
                "agent-a")).thenReturn(Optional.of(new SkillFrozenCandidateSnapshot(
                "candidate-1",
                "project-a",
                "agent-a",
                "candidate-hash-1",
                4,
                "[{\"section\":\"routingRules\"}]")));
        SkillCanaryContextApplicationService contextService =
                contextService(releasePort);

        String context = contextService.renderFrozen(
                "project-a",
                "agent-a",
                List.of(Map.of(
                        "candidateId", "candidate-1",
                        "releaseId", "release-1",
                        "projectId", "project-a",
                        "skillHash", "candidate-hash-1",
                        "version", 5)));

        assertTrue(context.contains("routingRules"));
    }

    @Test
    void frozenCanaryRefRejectsHashThatNoLongerResolves() {
        SkillReleasePort releasePort = mock(SkillReleasePort.class);
        when(releasePort.findFrozenCandidate(
                "candidate-1",
                "project-a",
                "forged-hash",
                "release-1",
                "agent-a")).thenReturn(Optional.empty());
        SkillCanaryContextApplicationService contextService =
                contextService(releasePort);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> contextService.renderFrozen(
                        "project-a",
                        "agent-a",
                        List.of(Map.of(
                                "candidateId", "candidate-1",
                                "releaseId", "release-1",
                                "projectId", "project-a",
                                "skillHash", "forged-hash",
                                "version", 5))));

        assertTrue(error.getMessage().contains("STALE_OR_TAMPERED"));
    }

    private SkillCatalogSnapshot snapshot(int version, String skillHash, String packageHash) {
        return SkillCatalogSnapshot.fromView(Map.ofEntries(
                Map.entry("skillId", "diagnosis"),
                Map.entry("projectId", "project-a"),
                Map.entry("scope", "PROJECT"),
                Map.entry("name", "Diagnosis"),
                Map.entry("description", "Collect evidence"),
                Map.entry("version", version),
                Map.entry("currentVersion", version),
                Map.entry("skillHash", skillHash),
                Map.entry("currentSkillHash", skillHash),
                Map.entry("currentPackageHash", packageHash),
                Map.entry("status", "ACTIVE"),
                Map.entry("updateMode", "AUTO"),
                Map.entry("content", "# Diagnosis")));
    }

    private SkillCanaryContextApplicationService contextService(
            SkillReleasePort releasePort) {
        return new SkillCanaryContextApplicationService(
                releasePort,
                new SkillCanaryApplicationService(
                        new SkillCanarySelectionPolicy(),
                        new SkillCanarySettings(true, 100)));
    }
}
