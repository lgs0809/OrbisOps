package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;
import cn.lgs.orbisops.domain.skill.model.SkillPackageKey;
import cn.lgs.orbisops.domain.skill.model.SkillPackageVersion;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SkillManagementUseCaseTest {

    @Test
    void createGlobalSkillOverridesUntrustedIdentityAndUsesMutationUseCase() {
        SkillCatalogPort port = mock(SkillCatalogPort.class);
        SkillCatalogMutationUseCase mutation = mock(SkillCatalogMutationUseCase.class);
        SkillManagementUseCase useCase = useCase(port, mutation);
        when(mutation.createGlobal(anyMap(), eq("alice")))
                .thenReturn(outcome("GLOBAL", "", "slow-sql", 1));
        when(port.getGlobalEntry("slow-sql"))
                .thenReturn(snapshot(Map.of("skillId", "slow-sql")));

        useCase.createGlobalSkill(Map.of(
                "skillId", "slow-sql",
                "content", "# Slow SQL",
                "createBy", "forged-user",
                "sourceTraceId", "forged-trace"), "alice");

        ArgumentCaptor<Map<String, Object>> command = mapCaptor();
        verify(mutation).createGlobal(command.capture(), eq("alice"));
        assertEquals("alice", command.getValue().get("createBy"));
        assertEquals("alice", command.getValue().get("sourceTraceId"));
        assertEquals("ENABLED", command.getValue().get("status"));
        assertEquals("MANUAL_ONLY", command.getValue().get("updateMode"));
        verify(port).getGlobalEntry("slow-sql");
    }

    @Test
    void updateGlobalSkillPreservesCreatorAndPassesCurrentSnapshot() {
        SkillCatalogPort port = mock(SkillCatalogPort.class);
        SkillCatalogMutationUseCase mutation = mock(SkillCatalogMutationUseCase.class);
        SkillManagementUseCase useCase = useCase(port, mutation);
        Map<String, Object> current = Map.of(
                "skillId", "slow-sql",
                "content", "old",
                "currentVersion", 3,
                "currentSkillHash", "hash-3",
                "status", "ENABLED",
                "updateMode", "MANUAL_ONLY",
                "createBy", "original-owner");
        when(port.getGlobalEntry("slow-sql"))
                .thenReturn(
                        snapshot(current),
                        snapshot(Map.of("skillId", "slow-sql", "version", 4)));

        useCase.updateGlobalSkill("slow-sql", Map.of(
                "description", "updated",
                "createBy", "forged-user"), "alice");

        ArgumentCaptor<Map<String, Object>> command = mapCaptor();
        verify(mutation).updateGlobal(eq("slow-sql"), eq(current), command.capture(), eq("alice"));
        assertEquals("original-owner", command.getValue().get("createBy"));
        assertEquals("alice", command.getValue().get("sourceTraceId"));
    }

    @Test
    void updateFileBackedSkillUsesActorWhenCreatorIsMissing() {
        SkillCatalogPort port = mock(SkillCatalogPort.class);
        SkillCatalogMutationUseCase mutation = mock(SkillCatalogMutationUseCase.class);
        SkillManagementUseCase useCase = useCase(port, mutation);
        Map<String, Object> current = Map.of(
                "skillId", "slow-sql",
                "content", "file content",
                "currentVersion", 1,
                "currentSkillHash", "file-hash-1",
                "status", "ENABLED",
                "updateMode", "MANUAL_ONLY");
        when(port.getGlobalEntry("slow-sql"))
                .thenReturn(
                        snapshot(current),
                        snapshot(Map.of("skillId", "slow-sql", "version", 2)));

        useCase.updateGlobalSkill("slow-sql", Map.of("description", "materialized"), "alice");

        ArgumentCaptor<Map<String, Object>> command = mapCaptor();
        verify(mutation).updateGlobal(eq("slow-sql"), eq(current), command.capture(), eq("alice"));
        assertEquals("alice", command.getValue().get("createBy"));
    }

    @Test
    void updateModeUsesVersionedMutationInsteadOfQueryPortWrite() {
        SkillCatalogPort port = mock(SkillCatalogPort.class);
        SkillCatalogMutationUseCase mutation = mock(SkillCatalogMutationUseCase.class);
        SkillManagementUseCase useCase = useCase(port, mutation);
        Map<String, Object> current = Map.of(
                "skillId", "slow-sql", "content", "content",
                "currentVersion", 3, "currentSkillHash", "hash-3",
                "status", "ACTIVE", "updateMode", "MANUAL_ONLY",
                "autoUpdateEnabled", false, "autoMergeEnabled", false,
                "createBy", "owner");
        when(port.getProjectEntry("project-1", "slow-sql"))
                .thenReturn(
                        snapshot(current),
                        snapshot(Map.of("skillId", "slow-sql", "version", 4)));

        useCase.updateProjectUpdateMode("project-1", "slow-sql",
                Map.of("updateMode", "AUTO", "autoUpdateEnabled", true), "alice");

        ArgumentCaptor<Map<String, Object>> command = mapCaptor();
        verify(mutation).updateProject(eq("project-1"), eq("slow-sql"),
                eq(current), command.capture(), eq("alice"));
        assertEquals("AUTO", command.getValue().get("updateMode"));
        assertEquals(true, command.getValue().get("autoUpdateEnabled"));
    }

    @Test
    void copyGlobalToProjectBuildsCreateCommandWithArtifactsAndManifestMetadata() {
        SkillCatalogPort port = mock(SkillCatalogPort.class);
        SkillCatalogMutationUseCase mutation = mock(SkillCatalogMutationUseCase.class);
        SkillPackageQueryService packageQuery = mock(SkillPackageQueryService.class);
        SkillManagementUseCase useCase = useCase(port, mutation, packageQuery);
        String manifest = "{\"dependencies\":[{\"type\":\"TOOLSET\",\"id\":\"database.read\",\"version\":\"1\"}],"
                + "\"evalSuites\":[\"evals/cases.json\"]}";
        when(port.getGlobalEntry("slow-sql")).thenReturn(snapshot(Map.of(
                "skillId", "slow-sql", "name", "Slow SQL", "content", "content",
                "currentVersion", 2, "currentSkillHash", "hash-2",
                "currentPackageHash", "package-2", "packageManifestJson", manifest,
                "status", "ACTIVE", "updateMode", "MANUAL_ONLY", "origin", "IMPORTED")));
        when(packageQuery.listArtifacts("", "slow-sql", 2, "hash-2", "package-2", "GLOBAL"))
                .thenReturn(List.of(
                        Map.of("path", "SKILL.md", "role", "ENTRYPOINT", "content", "content"),
                        Map.of("path", "resources/query.yaml", "role", "RESOURCE", "content", "query: slow\n"),
                        Map.of("path", "evals/cases.json", "role", "EVAL", "content", "[]")));
        when(mutation.createProject(eq("project-1"), anyMap(), eq("alice")))
                .thenReturn(outcome("PROJECT", "project-1", "project-1-slow-sql", 1));
        when(port.getProjectEntry("project-1", "project-1-slow-sql"))
                .thenReturn(snapshot(Map.of("skillId", "project-1-slow-sql")));

        useCase.copyGlobalToProject("project-1", "slow-sql", Map.of(), "alice");

        ArgumentCaptor<Map<String, Object>> command = mapCaptor();
        verify(mutation).createProject(eq("project-1"), command.capture(), eq("alice"));
        assertEquals("project-1-slow-sql", command.getValue().get("skillId"));
        assertEquals("slow-sql", command.getValue().get("sourceGlobalSkillId"));
        assertEquals("ENABLED", command.getValue().get("status"));
        assertEquals(2, ((List<?>) command.getValue().get("artifacts")).size());
        assertEquals(1, ((List<?>) command.getValue().get("dependencies")).size());
        assertEquals(List.of("evals/cases.json"), command.getValue().get("evalSuites"));
    }

    @Test
    void rollbackGlobalVersionDelegatesToRollbackUseCaseAndReloadsView() {
        SkillCatalogPort port = mock(SkillCatalogPort.class);
        SkillRollbackUseCase rollbackUseCase = mock(SkillRollbackUseCase.class);
        SkillManagementUseCase useCase = new SkillManagementUseCase(
                port, mock(SkillCatalogMutationUseCase.class), rollbackUseCase,
                mock(SkillEvolutionPublishUseCase.class), mock(SkillPackageQueryService.class));
        when(port.getGlobalEntry("slow-sql"))
                .thenReturn(snapshot(Map.of("skillId", "slow-sql", "version", 4)));

        Map<String, Object> result = useCase.rollbackGlobalVersion("slow-sql", 2, "alice");

        assertEquals(4, result.get("version"));
        verify(rollbackUseCase).rollbackGlobal("slow-sql", 2, "alice");
        verify(port).getGlobalEntry("slow-sql");
    }

    @Test
    void rollbackProjectVersionRejectsMissingActorBeforeUseCaseCall() {
        SkillRollbackUseCase rollbackUseCase = mock(SkillRollbackUseCase.class);
        SkillManagementUseCase useCase = new SkillManagementUseCase(
                mock(SkillCatalogPort.class), mock(SkillCatalogMutationUseCase.class),
                rollbackUseCase, mock(SkillEvolutionPublishUseCase.class),
                mock(SkillPackageQueryService.class));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> useCase.rollbackProjectVersion("project-1", "slow-sql", 2, " "));

        assertEquals("SKILL_ACTOR_REQUIRED", error.getMessage());
        verifyNoInteractions(rollbackUseCase);
    }

    @Test
    void evolutionPublishDelegatesToTypedUseCaseAndKeepsConflictMetadata() {
        SkillCatalogPort port = mock(SkillCatalogPort.class);
        SkillEvolutionPublishUseCase evolutionUseCase = mock(SkillEvolutionPublishUseCase.class);
        SkillManagementUseCase useCase = new SkillManagementUseCase(
                port, mock(SkillCatalogMutationUseCase.class),
                mock(SkillRollbackUseCase.class), evolutionUseCase,
                mock(SkillPackageQueryService.class));
        SkillCatalogEntry current = new SkillCatalogEntry(
                1L, "slow-sql", "project-1", "Slow SQL", "PROJECT", "",
                "desc", "content", 4, "ACTIVE", "owner", null, null,
                "EVOLVED", "AUTO", true, true, null, "", "", null,
                "hash-4", 4, "hash-4", 4, "package-4", "{}", "{}");
        when(evolutionUseCase.publishProject(
                eq("project-1"), eq("slow-sql"), anyMap(), eq(3), eq("hash-3"), eq("alice")))
                .thenReturn(new SkillEvolutionPublishOutcome(false, "MVCC_CONFLICT", current, null));
        when(port.getProjectEntry("project-1", "slow-sql"))
                .thenReturn(snapshot(Map.of("skillId", "slow-sql", "version", 4)));

        Map<String, Object> result = useCase.publishEvolvedProjectSkill(
                "project-1", "slow-sql", Map.of("content", "new"),
                3, "hash-3", "alice");

        assertEquals(false, result.get("published"));
        assertEquals("MVCC_CONFLICT", result.get("reasonCode"));
        assertEquals(4, result.get("currentVersion"));
        assertEquals("hash-4", result.get("currentSkillHash"));
    }

    @Test
    void createGlobalSkillRejectsMissingActor() {
        SkillCatalogMutationUseCase mutation = mock(SkillCatalogMutationUseCase.class);
        SkillManagementUseCase useCase = useCase(mock(SkillCatalogPort.class), mutation);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> useCase.createGlobalSkill(Map.of("skillId", "slow-sql"), " "));

        assertEquals("SKILL_ACTOR_REQUIRED", error.getMessage());
        verifyNoInteractions(mutation);
    }

    private SkillManagementUseCase useCase(SkillCatalogPort port,
                                            SkillCatalogMutationUseCase mutation) {
        return useCase(port, mutation, mock(SkillPackageQueryService.class));
    }

    private SkillManagementUseCase useCase(SkillCatalogPort port,
                                            SkillCatalogMutationUseCase mutation,
                                            SkillPackageQueryService packageQueryService) {
        return new SkillManagementUseCase(
                port, mutation, mock(SkillRollbackUseCase.class),
                mock(SkillEvolutionPublishUseCase.class), packageQueryService);
    }

    private SkillCatalogSnapshot snapshot(Map<String, Object> view) {
        Map<String, Object> runtime = new LinkedHashMap<>(view);
        String skillId = String.valueOf(runtime.getOrDefault("skillId", "skill"));
        runtime.putIfAbsent("version", 1);
        runtime.putIfAbsent("currentVersion", runtime.get("version"));
        runtime.putIfAbsent("skillHash", "hash-" + skillId);
        runtime.putIfAbsent("currentSkillHash", runtime.get("skillHash"));
        runtime.putIfAbsent("scope", "GLOBAL");
        runtime.putIfAbsent("name", skillId);
        runtime.putIfAbsent("description", skillId);
        runtime.putIfAbsent("content", "# " + skillId);
        runtime.putIfAbsent("status", "ACTIVE");
        runtime.putIfAbsent("updateMode", "AUTO");
        return new SkillCatalogSnapshot(
                new SkillRuntimeCandidateAssembler().fromView(runtime),
                view);
    }

    private SkillCatalogWriteOutcome outcome(String scope,
                                              String projectId,
                                              String skillId,
                                              int version) {
        SkillPackageVersion published = new SkillPackageVersion(
                0L, new SkillPackageKey(scope, projectId, skillId, version),
                "hash-" + version, Math.max(0, version - 1), "", "", "", "",
                "MANUAL_CREATE", "content", "MANUAL", "alice", "", "package-" + version,
                "{}", "{}", "SKILL.md", 1, 7L, null);
        return new SkillCatalogWriteOutcome(true, null, published);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return ArgumentCaptor.forClass((Class) Map.class);
    }
}
