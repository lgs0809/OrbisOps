package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillRuntimeCandidate;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillRuntimeCandidateAssemblerTest {

    private final SkillRuntimeCandidateAssembler assembler = new SkillRuntimeCandidateAssembler();

    @Test
    void assemblesExactRuntimeCandidateFromCatalogView() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("skillId", "diagnosis");
        view.put("projectId", "project-1");
        view.put("scope", "PROJECT");
        view.put("name", "Diagnosis");
        view.put("description", "Collect evidence");
        view.put("currentVersion", 4);
        view.put("currentSkillHash", "skill-hash-4");
        view.put("currentPackageHash", "package-hash-4");
        view.put("manifestHash", "manifest-hash-4");
        view.put("artifactHashes", Map.of("SKILL.md", "entry-hash", "resources/query.yaml", "query-hash"));
        view.put("entrypoint", "SKILL.md");
        view.put("status", "ACTIVE");
        view.put("updateMode", "MANUAL_ONLY");
        view.put("contentLength", 321);
        view.put("content", "# Diagnosis\n");
        view.put("whenToUse", java.util.List.of("收集故障证据"));
        view.put("whenNotToUse", java.util.List.of("生产写操作"));

        SkillRuntimeCandidate candidate = assembler.fromView(view);

        assertEquals("diagnosis", candidate.skillId());
        assertEquals("project-1", candidate.projectId());
        assertEquals("PROJECT", candidate.scope());
        assertEquals("Diagnosis", candidate.name());
        assertEquals(4, candidate.version());
        assertEquals("skill-hash-4", candidate.skillHash());
        assertEquals("package-hash-4", candidate.packageHash());
        assertEquals("manifest-hash-4", candidate.manifestHash());
        assertEquals("query-hash", candidate.artifactHashes().get("resources/query.yaml"));
        assertEquals("SKILL.md", candidate.entrypoint());
        assertEquals("ACTIVE", candidate.status());
        assertEquals("MANUAL_ONLY", candidate.updateMode());
        assertEquals(321, candidate.contentLength());
    }

    @Test
    void assemblesLegacyCatalogSkillButMarksRoutingBoundaryIncomplete() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("skillId", "slow-sql");
        view.put("skillName", "Slow SQL");
        view.put("scope", "GLOBAL");
        view.put("version", 2);
        view.put("skillHash", "skill-hash-2");
        view.put("status", "ENABLED");
        view.put("update_mode", "AUTO");
        view.put("markdown", "  # Slow SQL\n  ");
        view.put("artifactHashes", "not-json");

        SkillRuntimeCandidate candidate = assembler.fromView(view);

        assertEquals("slow-sql", candidate.skillId());
        assertEquals(2, candidate.version());
        assertTrue(!candidate.routingReady());
    }

    @Test
    void rejectsMissingRuntimeIdentity() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> assembler.fromView(Map.of("skillId", "diagnosis", "version", 1)));

        assertTrue(error.getMessage().contains("SKILL_RUNTIME_CANDIDATE_IDENTITY_REQUIRED"));
        assertThrows(IllegalArgumentException.class, () -> assembler.fromView(Map.of()));
    }
}
