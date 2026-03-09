package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;
import cn.lgs.orbisops.domain.skill.service.SkillPackageManifest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillCatalogViewMapperTest {

    private final SkillCatalogViewMapper mapper = new SkillCatalogViewMapper();

    @Test
    void mapsTypedEntryToLegacyCatalogViewWithoutTriggerDependencies() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 18, 9, 30);
        SkillPackageManifest.Descriptor descriptor = SkillPackageManifest.markdown(
                "PROJECT", "project-1", "diagnosis", "Diagnosis", "Collect evidence", 3, "# Diagnosis\n");
        SkillCatalogEntry entry = new SkillCatalogEntry(
                7L, "diagnosis", "project-1", "Diagnosis", "PROJECT", "global-diagnosis",
                "Collect evidence", "# Diagnosis\n", 3, "ACTIVE", "alice", createdAt, createdAt.plusHours(1),
                "EVOLVED", "MANUAL_ONLY", false, true, createdAt.plusMinutes(30),
                "manual freeze", "bob", createdAt.plusMinutes(45), "skill-hash-3", 3,
                "skill-hash-3", 3, "package-hash-3", descriptor.manifestJson(),
                com.alibaba.fastjson.JSON.toJSONString(descriptor.artifactHashes()));

        Map<String, Object> view = mapper.toView(entry, true);

        assertEquals(7L, view.get("id"));
        assertEquals("diagnosis", view.get("skillId"));
        assertEquals("Diagnosis", view.get("skillName"));
        assertEquals("PROJECT", view.get("scope"));
        assertEquals("project-1", view.get("projectId"));
        assertEquals("global-diagnosis", view.get("sourceGlobalSkillId"));
        assertEquals(3, view.get("currentVersion"));
        assertEquals("ACTIVE", view.get("status"));
        assertEquals("DB", view.get("sourceType"));
        assertEquals("EVOLVED", view.get("origin"));
        assertEquals("MANUAL_ONLY", view.get("updateMode"));
        assertEquals(false, view.get("autoUpdateEnabled"));
        assertEquals(true, view.get("autoMergeEnabled"));
        assertEquals("skill-hash-3", view.get("currentSkillHash"));
        assertEquals("package-hash-3", view.get("currentPackageHash"));
        assertEquals(descriptor.manifestJson(), view.get("packageManifestJson"));
        assertEquals(64, String.valueOf(view.get("manifestHash")).length());
        assertInstanceOf(Map.class, view.get("artifactHashes"));
        assertEquals("# Diagnosis\n", view.get("content"));
        assertTrue(String.valueOf(view.get("markdown")).contains("projectId: project-1"));
        assertInstanceOf(Map.class, view.get("frontMatter"));
    }

    @Test
    void preservesLegacyDefaultsAndOmitsContentForCompactView() {
        SkillCatalogEntry entry = new SkillCatalogEntry(
                1L, "slow-sql", "", "Slow SQL", "GLOBAL", "", "", "content", 1,
                "", "system", null, null, "", "unknown", true, false, null,
                "", "", null, "hash-1", 1, "", 1, "", "", "");

        Map<String, Object> view = mapper.toView(entry, false);

        assertEquals("ENABLED", view.get("status"));
        assertEquals("MANUAL", view.get("origin"));
        assertEquals("AUTO", view.get("updateMode"));
        assertEquals("hash-1", view.get("currentSkillHash"));
        assertEquals(view.get("currentPackageHash"), view.get("packageHash"));
        assertTrue(String.valueOf(view.get("currentPackageHash")).length() == 64);
        assertFalse(view.containsKey("content"));
        assertFalse(view.containsKey("markdown"));
    }
}
