package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.skill.SkillCatalogQueryService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
