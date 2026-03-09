package cn.lgs.orbisops.domain.skill.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillCatalogQueryPolicyTest {

    private final SkillCatalogQueryPolicy policy = new SkillCatalogQueryPolicy();

    @Test
    void databaseEntriesOverrideFileFallbacksAndPreserveStableOrder() {
        Candidate dbFirst = new Candidate("diagnosis", "DB", "db-1");
        Candidate dbReplacement = new Candidate("diagnosis", "DB", "db-2");
        Candidate dbOnly = new Candidate("slow-sql", "DB", "db-3");
        Candidate fileDuplicate = new Candidate("diagnosis", "FILE", "file-1");
        Candidate fileOnly = new Candidate("network", "FILE", "file-2");
        Candidate fileDuplicateLater = new Candidate("network", "FILE", "file-3");

        List<Candidate> result = policy.mergeDatabaseFirst(
                List.of(dbFirst, dbOnly, dbReplacement),
                List.of(fileDuplicate, fileOnly, fileDuplicateLater),
                Candidate::skillId);

        assertEquals(List.of(dbReplacement, dbOnly, fileOnly), result);
    }

    @Test
    void ignoresNullBlankCandidatesAndAcceptsNullLists() {
        List<Candidate> result = policy.mergeDatabaseFirst(
                null,
                java.util.Arrays.asList(null, new Candidate(" ", "FILE", "blank"),
                        new Candidate("diagnosis", "FILE", "valid")),
                Candidate::skillId);

        assertEquals(List.of(new Candidate("diagnosis", "FILE", "valid")), result);
    }

    @Test
    void matchesGlobalAndProjectFileScopesWithoutLeakingAcrossProjects() {
        assertTrue(policy.isGlobalFileSkill("global", ""));
        assertFalse(policy.isGlobalFileSkill("GLOBAL", "project-1"));
        assertFalse(policy.isGlobalFileSkill("PROJECT", ""));

        assertTrue(policy.isProjectFileSkill("project", "project-1", "project-1"));
        assertFalse(policy.isProjectFileSkill("PROJECT", "project-2", "project-1"));
        assertFalse(policy.isProjectFileSkill("GLOBAL", "project-1", "project-1"));
        assertFalse(policy.isProjectFileSkill("PROJECT", "project-1", " "));
    }

    private record Candidate(String skillId, String source, String value) {
    }
}
