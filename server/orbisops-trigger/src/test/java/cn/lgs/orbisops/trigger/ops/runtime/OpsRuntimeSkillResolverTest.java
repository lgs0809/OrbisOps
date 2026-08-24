package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.project.ProjectSkillAuthorizationApplicationService;
import cn.lgs.orbisops.application.skill.SkillCatalogQueryService;
import cn.lgs.orbisops.trigger.ops.skill.OpsSkillReleaseService;
import cn.lgs.orbisops.trigger.ops.skill.OpsSkillToolProvider;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsRuntimeSkillResolverTest {

    @Test
    void emptyBindingsAndFrozenContextMustExposeNothing() {
        OpsSkillToolProvider toolProvider = mock(OpsSkillToolProvider.class);
        OpsRuntimeSkillResolver resolver = resolver(
                toolProvider, null, null, null, null, settings(true, "lazy"));
        OpsRuntimeResourceContext context = context(List.of(), Map.of());

        resolver.resolve(context);

        assertEquals("none", context.getMetadata().get("skillContextMode"));
        assertTrue(context.getTools().isEmpty());
        assertNull(context.getSkillContext());
        verify(toolProvider, never()).buildSkillToolCallback(anyCollection());
    }

    @Test
    void liveFileNamesWithoutFrozenGovernedVersionsMustNotExposeToolOrBody() {
        OpsSkillToolProvider toolProvider = mock(OpsSkillToolProvider.class);
        OpsRuntimeSkillResolver resolver = resolver(toolProvider, null, null, null, null, settings(true, "full"));
        OpsRuntimeResourceContext context = context(List.of("repair"), Map.of());
        resolver.resolve(context);
        assertTrue(context.getTools().isEmpty());
        assertNull(context.getSkillContext());
        verify(toolProvider, never()).buildSkillToolCallback(anyCollection());
        verify(toolProvider, never()).renderSkillContext(anyCollection(), anyInt());
    }

    @Test
    void disabledContextMustNotReadOrExposeUnfrozenFiles() {
        OpsSkillToolProvider toolProvider = mock(OpsSkillToolProvider.class);
        OpsRuntimeSkillResolver resolver = resolver(toolProvider, null, null, null, null, settings(false, "full"));
        OpsRuntimeResourceContext context = context(List.of("repair"), Map.of());
        resolver.resolve(context);
        assertTrue(context.getTools().isEmpty());
        assertEquals("disabled", context.getMetadata().get("skillContextMode"));
        verify(toolProvider, never()).renderSkillContext(anyCollection(), anyInt());
    }

    @Test
    void frozenPublishedAndCanaryRefsMustMaterializeExactContext() {
        SkillCatalogQueryService catalogQuery = mock(SkillCatalogQueryService.class);
        OpsSkillReleaseService releaseService = mock(OpsSkillReleaseService.class);
        when(catalogQuery.getRuntimeSkillVersion(
                "project-1", "skill-1", 3, "skill-hash", "package-hash", "PROJECT"))
                .thenReturn(Map.of(
                        "name", "Published Skill",
                        "content", "published guidance"));
        when(releaseService.renderFrozenCanaryContext(
                eq("project-1"), eq("agent-1"), anyList()))
                .thenReturn("canary guidance");
        Map<String, Object> metadata = Map.of(
                "skillCatalogRefs", List.of(Map.of(
                        "skillId", "skill-1",
                        "name", "Published Skill",
                        "description", "desc",
                        "category", "OBSERVABILITY",
                        "whenNotToUse", List.of("生产写操作"))),
                "usedSkillVersionRefs", List.of(
                        Map.of(
                                "skillId", "skill-1",
                                "version", 3,
                                "skillHash", "skill-hash",
                                "packageHash", "package-hash",
                                "scope", "PROJECT",
                                "statusAtUse", "PUBLISHED"),
                        Map.of(
                                "skillId", "candidate:candidate-1",
                                "version", 4,
                                "skillHash", "candidate-hash",
                                "packageHash", "candidate-package-hash",
                                "scope", "PROJECT",
                                "statusAtUse", "CANARY")));
        OpsProjectSkillToolProvider provider = mock(OpsProjectSkillToolProvider.class);
        when(provider.build(eq("project-1"), eq("alice"), eq("run-1"), anyList())).thenReturn(mock(ToolCallback.class));
        OpsRuntimeSkillResolver resolver = resolver(
                null, catalogQuery, null, releaseService, provider, settings(true, "summary"));
        OpsRuntimeResourceContext context = context(List.of(), metadata);

        resolver.resolve(context);

        assertEquals("summary", context.getMetadata().get("skillContextMode"));
        assertTrue(context.getSkillContext().contains("Skill 轻量目录"));
        assertTrue(context.getSkillContext().contains("[OBSERVABILITY]"));
        assertTrue(context.getSkillContext().contains("不适用：生产写操作"));
        assertTrue(context.getSkillContext().contains("published guidance"));
        assertTrue(context.getSkillContext().contains("canary guidance"));
        verify(catalogQuery).getRuntimeSkillVersion(
                "project-1", "skill-1", 3, "skill-hash", "package-hash", "PROJECT");
    }

    @Test
    void catalogRefsMustUseProjectSkillToolWithoutLeakingIntoAssembler() {
        SkillCatalogQueryService catalogQuery = mock(SkillCatalogQueryService.class);
        OpsProjectSkillToolProvider projectToolProvider = mock(OpsProjectSkillToolProvider.class);
        ToolCallback callback = mock(ToolCallback.class);
        List<Map<String, Object>> refs = List.of(Map.of(
                "skillId", "skill-1",
                "name", "Skill One"));
        when(projectToolProvider.build(
                eq("project-1"), eq("alice"), eq("run-1"), eq(refs)))
                .thenReturn(callback);
        OpsRuntimeSkillResolver resolver = resolver(
                null, catalogQuery, null, null, projectToolProvider, settings(true, "lazy"));
        OpsRuntimeResourceContext context = context(
                List.of(), Map.of("skillCatalogRefs", refs));

        resolver.resolve(context);

        assertSame(callback, ((OpsRuntimeGovernedToolCallback) context.getTools().get(0)).delegate());
        verify(projectToolProvider).build(
                "project-1", "alice", "run-1", refs);
    }

    @Test
    void projectInheritanceMustUseAuthorizationCatalog() {
        ProjectSkillAuthorizationApplicationService authorization =
                mock(ProjectSkillAuthorizationApplicationService.class);
        when(authorization.enabledIds("project-1"))
                .thenReturn(List.of("skill-a", "skill-b"));
        OpsRuntimeSkillResolver resolver = resolver(
                null, null, authorization, null, null, settings(true, "lazy"));

        assertEquals(List.of("skill-a", "skill-b"),
                resolver.enabledProjectSkillIds("project-1"));
    }

    private OpsRuntimeResourceContext context(
            List<String> skills,
            Map<String, Object> metadata) {
        return OpsRuntimeResourceContext.builder()
                .definition(OpsAgentDefinition.builder().agentId("agent-1").build())
                .request(OpsAgentChatRequest.builder()
                        .projectId("project-1")
                        .userId("alice")
                        .runId("run-1")
                        .metadata(new java.util.LinkedHashMap<>(metadata))
                        .build())
                .projectId("project-1")
                .skillNames(new LinkedHashSet<>(skills))
                .events(new ArrayList<>())
                .build();
    }

    private OpsRuntimeSkillResolver resolver(
            OpsSkillToolProvider toolProvider,
            SkillCatalogQueryService catalogQuery,
            ProjectSkillAuthorizationApplicationService authorization,
            OpsSkillReleaseService releaseService,
            OpsProjectSkillToolProvider projectToolProvider,
            OpsRuntimeSkillSettings settings) {
        if (catalogQuery != null) when(catalogQuery.retainRuntimeCatalogRefs(eq("project-1"), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        OpsRuntimeFrozenSkillContextResolver frozenContextResolver =
                new OpsRuntimeFrozenSkillContextResolver(
                        () -> catalogQuery,
                        () -> releaseService,
                        settings, mock(cn.lgs.orbisops.application.skill.SkillRuntimeBudgetPort.class));
        return new OpsRuntimeSkillResolver(
                () -> toolProvider,
                () -> authorization,
                () -> projectToolProvider,
                frozenContextResolver,
                settings);
    }

    private OpsRuntimeSkillSettings settings(boolean enabled, String mode) {
        return OpsRuntimeSkillSettings.forTest(enabled, mode, 4000, 1000, 1000);
    }
}
