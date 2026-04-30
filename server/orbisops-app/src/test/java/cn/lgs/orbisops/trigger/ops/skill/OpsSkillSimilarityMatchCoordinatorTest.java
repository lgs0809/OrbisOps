package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.skill.SkillCatalogQueryService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsSkillSimilarityMatchCoordinatorTest {

    @Test
    void frozenGlobalMatchTakesPrecedenceOverProjectMatch() {
        SkillCatalogQueryService catalog = mock(SkillCatalogQueryService.class);
        Map<String, Object> projectSkill = OpsSkillSimilarityTestFixtures.skill(
                "project-order",
                "OPERATIONS",
                "ACTIVE");
        Map<String, Object> frozenGlobal = OpsSkillSimilarityTestFixtures.skill(
                "global-order",
                "OPERATIONS",
                "FROZEN");
        when(catalog.listProjectSkills("demo-project"))
                .thenReturn(List.of(Map.of("skillId", "project-order")));
        when(catalog.getProjectSkill("demo-project", "project-order"))
                .thenReturn(projectSkill);
        when(catalog.listGlobalSkills())
                .thenReturn(List.of(Map.of(
                        "skillId", "global-order",
                        "status", "FROZEN")));
        when(catalog.getGlobalSkill("global-order"))
                .thenReturn(frozenGlobal);
        OpsSkillSimilarityMatchCoordinator coordinator =
                new OpsSkillSimilarityMatchCoordinator(
                        catalog,
                        new OpsSkillSimilaritySettings(0.1D),
                        null);

        Map<String, Object> result = coordinator.bestMatch(
                "demo-project",
                OpsSkillSimilarityTestFixtures.candidate("OPERATIONS"));

        assertEquals("global-order", result.get("skillId"));
        assertEquals("FROZEN_SIMILARITY_GUARD", result.get("similarityReason"));
        assertTrue(((Number) result.get("similarity")).doubleValue() >= 0.1D);
    }

    @Test
    void governedMatcherUsesOnlyFrozenPackagesAndProtectsGlobalAndManualMethods() {
        var catalog=mock(SkillCatalogQueryService.class);
        var coordinator=new OpsSkillFrozenSimilarityMatcher(new OpsSkillSimilaritySettings(0.1D),null);
        var project=new java.util.LinkedHashMap<>(OpsSkillSimilarityTestFixtures.skill("project","OPERATIONS","ENABLED"));
        project.put("scope","PROJECT");project.put("projectId","p");project.put("sourceType","DB");
        project.put("currentVersion",1);project.put("catalogFence","c".repeat(64));project.put("relatedArtifacts",List.of());
        project.put("currentSkillHash","a".repeat(64));project.put("currentPackageHash","b".repeat(64));project.put("autoUpdateEnabled",true);
        var selected=new java.util.LinkedHashMap<>(OpsSkillSimilarityTestFixtures.candidate("OPERATIONS"));
        selected.put("targetSkillId","project");
        var result=coordinator.bestMatch("p",selected,List.of(project));
        assertEquals("project",result.get("skillId"));assertEquals("AUTHOR_SELECTED_FROZEN_TARGET",result.get("similarityReason"));
        assertEquals(Map.of(),coordinator.bestMatch("p",OpsSkillSimilarityTestFixtures.candidate("OPERATIONS"),List.of(project)));
        // Even a deliberately high threshold cannot silently change a PATCH into CREATE.
        assertEquals("project",new OpsSkillFrozenSimilarityMatcher(new OpsSkillSimilaritySettings(0.99D),null)
                .bestMatch("p",selected,List.of(project)).get("skillId"));
        var missing=new java.util.LinkedHashMap<>(selected);missing.put("targetSkillId","not-frozen");
        assertThrows(IllegalStateException.class,()->coordinator.bestMatch("p",missing,List.of(project)));
        var global=new java.util.LinkedHashMap<>(project);global.put("scope","GLOBAL");global.put("projectId","");global.put("skillId","global");
        assertEquals(Map.of(),coordinator.bestMatch("p",OpsSkillSimilarityTestFixtures.candidate("OPERATIONS"),List.of(global)));
        global.put("status","FROZEN");global.put("updateMode","FROZEN");
        result=coordinator.bestMatch("p",OpsSkillSimilarityTestFixtures.candidate("OPERATIONS"),List.of(project,global));
        assertEquals("global",result.get("skillId"));assertEquals(true,result.get("frozen"));
        project.put("updateMode","MANUAL_ONLY");
        result=coordinator.bestMatch("p",OpsSkillSimilarityTestFixtures.candidate("OPERATIONS"),List.of(project));
        assertEquals(true,result.get("frozen"));
        org.mockito.Mockito.verifyNoInteractions(catalog);
    }

    @Test
    void legacyServicePreservesZeroThresholdEmptyCatalogProjection() {
        SkillCatalogQueryService catalog = mock(SkillCatalogQueryService.class);
        when(catalog.listProjectSkills("demo-project")).thenReturn(List.of());
        when(catalog.listGlobalSkills()).thenReturn(List.of());
        OpsSkillSimilarityService service = new OpsSkillSimilarityService(catalog);

        Map<String, Object> result = service.bestMatch(
                "demo-project",
                OpsSkillSimilarityTestFixtures.candidate("OPERATIONS"));

        assertEquals(0D, result.get("similarity"));
        assertEquals("BEST_PROJECT_MATCH", result.get("similarityReason"));
    }
}
