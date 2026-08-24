package cn.lgs.orbisops.application.skill;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SelectRuntimeSkillsQueryTest {

    @Test
    void boundsCompactCatalogAndSelectsNothingForEmptyQuery() {
        SkillCatalogPort catalog = mock(SkillCatalogPort.class);
        when(catalog.listRuntimeProjectEntries("demo-project")).thenReturn(List.of(
                runtimeSkill("mysql-slow-sql", "MySQL 慢 SQL 排查", "ACTIVE", "PROJECT"),
                runtimeSkill("prometheus-errors", "Prometheus 错误率排查", "ACTIVE", "PROJECT"),
                runtimeSkill("elk-errors", "ELK 错误日志排查", "ACTIVE", "PROJECT"),
                runtimeSkill("docker-health", "Docker 健康检查", "ACTIVE", "PROJECT")));
        when(catalog.listRuntimeGlobalEntries()).thenReturn(List.of());
        SelectRuntimeSkillsQuery query = query(catalog, settings(3, 2, 12, 0.12D, 0.88D));

        SelectRuntimeSkillsQuery.Result result = query.select(
                new SelectRuntimeSkillsQuery.Request("demo-project", "ops-agent", List.of(), "", 2));

        assertEquals(4, result.activeCount());
        assertEquals(3, result.catalogCount());
        assertTrue(result.selectedRefs().isEmpty());
    }

    @Test
    void selectsRelevantSkillsAndSuppressesNearDuplicates() {
        SkillCatalogPort catalog = mock(SkillCatalogPort.class);
        when(catalog.listRuntimeProjectEntries("demo-project")).thenReturn(List.of(
                runtimeSkill("mysql-slow-sql", "MySQL 慢 SQL 查询与证据收集", "ACTIVE", "PROJECT"),
                runtimeSkill("mysql-slow-query", "MySQL 慢 SQL 查询和证据收集", "ACTIVE", "PROJECT"),
                runtimeSkill("prometheus-errors", "Prometheus 错误率排查", "ACTIVE", "PROJECT")));
        when(catalog.listRuntimeGlobalEntries()).thenReturn(List.of());
        SelectRuntimeSkillsQuery query = query(catalog, settings(24, 4, 12, 0.01D, 0.72D));

        SelectRuntimeSkillsQuery.Result result = query.select(
                new SelectRuntimeSkillsQuery.Request("demo-project", "ops-agent", List.of(), "排查 MySQL 慢 SQL", 4));

        assertTrue(result.selectedRefs().stream()
                .anyMatch(ref -> "mysql-slow-sql".equals(ref.get("skillId"))));
        assertEquals(1, result.suppressedRefs().stream().filter(r->"SIMILAR_SKILL_SUPPRESSED".equals(r.get("reasonCode"))).count());

    }

    @Test
    void disabledProjectSkillShadowsActiveGlobalSkill() {
        SkillCatalogPort catalog = mock(SkillCatalogPort.class);
        when(catalog.listRuntimeProjectEntries("demo-project")).thenReturn(List.of(
                runtimeSkill("slow-sql", "项目已停用", "DISABLED", "PROJECT")));
        when(catalog.listRuntimeGlobalEntries()).thenReturn(List.of(
                runtimeSkill("slow-sql", "全局慢 SQL", "ACTIVE", "GLOBAL")));
        SelectRuntimeSkillsQuery query = query(catalog, settings(24, 6, 12, 0.01D, 0.88D));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> query.select(
                new SelectRuntimeSkillsQuery.Request(
                        "demo-project", "ops-agent", List.of("slow-sql"), "慢 SQL", 6)));

        assertTrue(error.getMessage().contains("REQUESTED_SKILL_NOT_ACTIVE"));
    }

    @Test
    void explicitSkillsAreNotDroppedByAutomaticSelectionLimit() {
        SkillCatalogPort catalog = mock(SkillCatalogPort.class);
        when(catalog.listRuntimeProjectEntries("demo-project")).thenReturn(List.of(
                runtimeSkill("mysql-slow-sql", "MySQL 慢 SQL 查询与证据收集", "ACTIVE", "PROJECT"),
                runtimeSkill("mysql-slow-query", "MySQL 慢 SQL 查询和证据收集", "ACTIVE", "PROJECT")));
        when(catalog.listRuntimeGlobalEntries()).thenReturn(List.of());
        SelectRuntimeSkillsQuery query = query(catalog, settings(24, 1, 12, 0.01D, 0.60D));

        SelectRuntimeSkillsQuery.Result result = query.select(new SelectRuntimeSkillsQuery.Request(
                "demo-project", "ops-agent", List.of("mysql-slow-sql", "mysql-slow-query"),
                "排查 MySQL 慢 SQL", 1));

        assertEquals(2, result.selectedRefs().size());
        assertTrue(result.selectedRefs().stream()
                .allMatch(ref -> "REQUESTED_ACTIVE_SKILL".equals(ref.get("selectedReason"))));
        assertTrue(result.suppressedRefs().isEmpty());
        assertTrue(result.selectedRefs().stream()
                .allMatch(ref -> String.valueOf(ref.get("packageHash")).length() == 64));
    }

    @Test
    void rejectsTooManyExplicitSkillsBeforeCatalogResolution() {
        SkillCatalogPort catalog = mock(SkillCatalogPort.class);
        SelectRuntimeSkillsQuery query = query(catalog, settings(24, 6, 2, 0.01D, 0.88D));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> query.select(
                new SelectRuntimeSkillsQuery.Request(
                        "demo-project", "ops-agent", List.of("one", "two", "three"), "query", 6)));

        assertTrue(error.getMessage().contains("TOO_MANY_EXPLICIT_SKILLS"));
        verifyNoInteractions(catalog);
    }

    @Test
    void semanticScoresCannotInventApplicabilityForUnrelatedQuery() {
        SkillCatalogPort catalog = mock(SkillCatalogPort.class);
        when(catalog.listRuntimeProjectEntries("demo-project")).thenReturn(List.of(
                runtimeSkill("mysql-slow-sql", "MySQL evidence", "ACTIVE", "PROJECT")));
        when(catalog.listRuntimeGlobalEntries()).thenReturn(List.of());
        SkillSemanticScorePort semantic = mock(SkillSemanticScorePort.class);
        when(semantic.scores(anyString(),anyString(), anyList())).thenReturn(Map.of("mysql-slow-sql", 0.95D));
        SelectRuntimeSkillsQuery query = new SelectRuntimeSkillsQuery(
                catalog, semantic, settings(24, 2, 12, 0.01D, 0.88D));

        SelectRuntimeSkillsQuery.Result result = query.select(
                new SelectRuntimeSkillsQuery.Request("demo-project", "ops-agent", List.of(), "database issue", 2));

        verify(semantic).scores(anyString(),anyString(), anyList());
        assertTrue(result.selectedRefs().isEmpty());
    }

    @Test
    void semanticRecallUsesRelevantCandidatesFromEntireLargeCatalog() {
        SkillCatalogPort catalog = mock(SkillCatalogPort.class);
        List<SkillCatalogSnapshot> skills = new ArrayList<>();
        for (int index = 0; index < 150; index++) {
            skills.add(runtimeSkill(
                    "code-" + index,
                    "Java 代码生成和代码审查。不支持制作 PPT",
                    "ACTIVE",
                    "PROJECT"));
        }
        skills.add(runtimeSkill(
                "quarterly-ppt",
                "制作季度汇报、融资路演和产品演示文稿。不支持后端接口开发",
                "ACTIVE",
                "PROJECT"));
        when(catalog.listRuntimeProjectEntries("demo-project")).thenReturn(skills);
        when(catalog.listRuntimeGlobalEntries()).thenReturn(List.of());
        SkillSemanticScorePort semantic = mock(SkillSemanticScorePort.class);
        when(semantic.scores(anyString(),anyString(), anyList())).thenReturn(Map.of("quarterly-ppt", 0.99D));
        SkillRuntimeSelectionSettings settings = new SkillRuntimeSelectionSettings(
                24, 6, 12, 0.05D, 0.88D, 0.42D,
                16, 0.12D, 0.65D);
        SelectRuntimeSkillsQuery query = new SelectRuntimeSkillsQuery(catalog, semantic, settings);

        SelectRuntimeSkillsQuery.Result result = query.select(
                new SelectRuntimeSkillsQuery.Request(
                        "demo-project", "ops-agent", List.of(),
                        "帮我生成季度汇报的 PPT", 6));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<cn.lgs.orbisops.domain.skill.model.SkillRuntimeCandidate>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(semantic).scores(anyString(),anyString(), captor.capture());
        assertEquals(151, captor.getValue().size());
        assertTrue(captor.getValue().stream()
                .anyMatch(candidate -> "quarterly-ppt".equals(candidate.skillId())));
        assertTrue(result.catalogRefs().stream().anyMatch(r->"quarterly-ppt".equals(r.get("skillId"))));
        // A synthetic vector score is not independent proof of applicability.
        assertTrue(result.suppressedRefs().stream().anyMatch(r->"SKILL_APPLICABILITY_NEED_INFO".equals(r.get("reasonCode"))));
    }

    @Test
    void negativeUseCasePreventsAdjacentSkillMisrouting() {
        SkillCatalogPort catalog = mock(SkillCatalogPort.class);
        Map<String, Object> frontendView = runtimeSkillView(
                "frontend-page",
                "生成前端页面代码。不支持后端接口开发",
                "ACTIVE",
                "PROJECT");
        frontendView.put("whenNotToUse", List.of("后端接口开发", "数据库迁移"));
        SkillCatalogSnapshot frontend = SkillCatalogSnapshot.fromView(frontendView);
        SkillCatalogSnapshot backend = runtimeSkill(
                "backend-api",
                "开发 Java 后端接口和服务",
                "ACTIVE",
                "PROJECT");
        when(catalog.listRuntimeProjectEntries("demo-project")).thenReturn(List.of(frontend, backend));
        when(catalog.listRuntimeGlobalEntries()).thenReturn(List.of());
        SelectRuntimeSkillsQuery query = query(
                catalog,
                new SkillRuntimeSelectionSettings(
                        24, 1, 12, 0.01D, 0.88D, 0D,
                        16, 0.12D, 0.9D));

        SelectRuntimeSkillsQuery.Result result = query.select(
                new SelectRuntimeSkillsQuery.Request(
                        "demo-project", "ops-agent", List.of(),
                        "帮我开发后端接口", 1));

        assertEquals("backend-api", result.selectedRefs().get(0).get("skillId"));
    }

    @Test
    void activeProjectSkillWithoutRoutingBoundaryShadowsGlobalAndFailsClosed() {
        SkillCatalogPort catalog = mock(SkillCatalogPort.class);
        Map<String, Object> projectView = runtimeSkillView(
                "slow-sql", "legacy project skill", "ACTIVE", "PROJECT");
        projectView.remove("whenToUse");
        projectView.remove("whenNotToUse");
        SkillCatalogSnapshot legacyProject = SkillCatalogSnapshot.fromView(projectView);
        when(catalog.listRuntimeProjectEntries("demo-project")).thenReturn(List.of(legacyProject));
        when(catalog.listRuntimeGlobalEntries()).thenReturn(List.of(
                runtimeSkill("slow-sql", "global slow sql", "ACTIVE", "GLOBAL")));
        SelectRuntimeSkillsQuery query = query(catalog, settings(24, 6, 12, 0.01D, 0.88D));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> query.select(
                new SelectRuntimeSkillsQuery.Request(
                        "demo-project", "ops-agent", List.of("slow-sql"), "slow sql", 6)));

        assertTrue(!legacyProject.runtimeCandidate().routingReady());
        assertTrue(error.getMessage().contains("REQUESTED_SKILL_NOT_ACTIVE"));
    }

    @Test
    void frozenCatalogWithoutRoutingBoundaryIsRejectedAtRuntime() {
        SkillCatalogPort catalog = mock(SkillCatalogPort.class);
        SelectRuntimeSkillsQuery query = query(catalog, settings(24, 6, 12, 0.01D, 0.88D));
        Map<String, Object> legacy = runtimeSkillView(
                "legacy-skill", "legacy skill", "ACTIVE", "PROJECT");
        legacy.remove("whenToUse");
        legacy.remove("whenNotToUse");

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> query.selectFrozen(
                new SelectRuntimeSkillsQuery.FrozenRequest("demo-project", List.of(legacy), "legacy", 1)));

        assertEquals("SKILL_ROUTING_PROFILE_REQUIRED_AT_RUNTIME:legacy-skill", error.getMessage());
    }

    @Test
    void ranksFrozenVersionsAfterRecheckingCurrentGovernance() {
        SkillCatalogPort catalog = mock(SkillCatalogPort.class);
        SelectRuntimeSkillsQuery query = query(catalog, settings(24, 6, 12, 0.01D, 0.88D));
        List<Map<String, Object>> frozen = List.of(
                runtimeSkillView("mysql-slow-sql", "MySQL slow SQL", "ACTIVE", "PROJECT"),
                runtimeSkillView("prometheus-errors", "Prometheus error rate", "ACTIVE", "PROJECT"));

        when(catalog.listRuntimeProjectEntries("demo-project")).thenReturn(frozen.stream().map(SkillCatalogSnapshot::fromView).toList());
        when(catalog.listRuntimeGlobalEntries()).thenReturn(List.of());
        SelectRuntimeSkillsQuery.Result result = query.selectFrozen(
                new SelectRuntimeSkillsQuery.FrozenRequest("demo-project", frozen, "mysql slow", 1));

        assertEquals(2, result.catalogCount());
        assertEquals(1, result.selectedCount());
        assertEquals("mysql-slow-sql", result.selectedRefs().get(0).get("skillId"));
        assertTrue(result.selectedRefs().get(0).containsKey("queryScore"));
    }

    private SelectRuntimeSkillsQuery query(SkillCatalogPort catalog, SkillRuntimeSelectionSettings settings) {
        return new SelectRuntimeSkillsQuery(catalog, (query, candidates) -> Map.of(), settings);
    }

    private SkillRuntimeSelectionSettings settings(int catalogLimit,
                                                    int selectedLimit,
                                                    int maxExplicit,
                                                    double minScore,
                                                    double suppressionThreshold) {
        return new SkillRuntimeSelectionSettings(
                catalogLimit, selectedLimit, maxExplicit, minScore, suppressionThreshold, 0.42D);
    }

    private SkillCatalogSnapshot runtimeSkill(String skillId,
                                              String description,
                                              String status,
                                              String scope) {
        return SkillCatalogSnapshot.fromView(runtimeSkillView(skillId, description, status, scope));
    }

    private Map<String, Object> runtimeSkillView(String skillId,
                                                 String description,
                                                 String status,
                                                 String scope) {
        Map<String, Object> skill = new LinkedHashMap<>();
        skill.put("skillId", skillId);
        skill.put("projectId", "PROJECT".equals(scope) ? "demo-project" : "");
        skill.put("scope", scope);
        skill.put("name", skillId);
        skill.put("description", description);
        skill.put("whenToUse", List.of(description));
        skill.put("whenNotToUse", List.of("与该方法无关的任务"));
        skill.put("keywords", List.of(skillId));
        skill.put("status", status);
        skill.put("updateMode", "AUTO");
        skill.put("version", 1);
        skill.put("currentVersion", 1);
        skill.put("skillHash", "hash-" + skillId);
        skill.put("currentSkillHash", "hash-" + skillId);
        skill.put("content", "# " + skillId + "\n" + description);
        return skill;
    }
}
