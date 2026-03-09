package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.service.SkillCatalogFingerprint;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillFileCatalogViewMapperTest {

    private final SkillFileCatalogViewMapper mapper = new SkillFileCatalogViewMapper();

    @Test
    void mapsFileSnapshotWithoutLeakingExternalSdkTypes() {
        SkillFileDefinition skill = new SkillFileDefinition(
                "diagnosis", "/skills/diagnosis",
                Map.of("name", "diagnosis", "description", "Collect evidence", "scope", "PROJECT",
                        "project_id", "demo-project"),
                "  # Diagnosis\n  ", "---\nname: diagnosis\n---\n\n# Diagnosis\n", "<skill/>");

        Map<String, Object> view = mapper.toView(skill, true);

        assertEquals("diagnosis", view.get("skillId"));
        assertEquals("PROJECT", view.get("scope"));
        assertEquals("demo-project", view.get("projectId"));
        assertEquals("FILE", view.get("sourceType"));
        assertEquals("IMPORTED", view.get("origin"));
        assertEquals("MANUAL_ONLY", view.get("updateMode"));
        assertEquals(false, view.get("autoUpdateEnabled"));
        assertEquals("  # Diagnosis\n  ", view.get("content"));
        assertEquals("<skill/>", view.get("xml"));
        assertEquals(SkillCatalogFingerprint.sha256(
                "PROJECT", "demo-project", "diagnosis", "diagnosis", "Collect evidence",
                "  # Diagnosis\n  ", 1, "ENABLED", "MANUAL_ONLY", false, false),
                view.get("skillHash"));
        assertTrue(String.valueOf(view.get("currentPackageHash")).length() == 64);
        assertInstanceOf(Map.class, view.get("artifactHashes"));
    }

    @Test
    void compactViewOmitsContentAndDefaultsToGlobalScope() {
        SkillFileDefinition skill = new SkillFileDefinition(
                "slow-sql", "/skills/slow-sql", Map.of("description", "Slow SQL"),
                "# Slow SQL", "", "");

        Map<String, Object> view = mapper.toView(skill, false);

        assertEquals("GLOBAL", view.get("scope"));
        assertEquals("", view.get("projectId"));
        assertFalse(view.containsKey("content"));
        assertFalse(view.containsKey("markdown"));
        assertFalse(view.containsKey("xml"));
        assertEquals(1, view.get("artifactCount"));
    }
}
