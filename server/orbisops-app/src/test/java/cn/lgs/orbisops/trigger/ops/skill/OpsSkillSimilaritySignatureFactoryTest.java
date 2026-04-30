package cn.lgs.orbisops.trigger.ops.skill;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsSkillSimilaritySignatureFactoryTest {

    private final OpsSkillSimilaritySignatureFactory factory =
            new OpsSkillSimilaritySignatureFactory();

    @Test
    void projectsCandidateAndSkillRoutingSignaturesAndSectionKeys() {
        OpsSkillSimilaritySignatureFactory.CandidateContext candidate =
                factory.candidate(OpsSkillSimilarityTestFixtures.candidate("OPERATIONS"));
        OpsSkillSimilaritySignatureFactory.SkillContext skill =
                factory.skill(OpsSkillSimilarityTestFixtures.skill(
                        "order-recovery",
                        "OPERATIONS",
                        "ACTIVE"));

        assertEquals("OPERATIONS", candidate.profile().category());
        assertEquals("UPDATE_SKILL_CANDIDATE", candidate.patchType());
        assertTrue(candidate.signature().contains("订单失败排查"));
        assertTrue(candidate.sectionKeys().contains("diagnosticRecipe:steps"));
        assertEquals("order-recovery", skill.skillId());
        assertEquals("order-recovery-hash", skill.cacheKey());
        assertTrue(skill.sectionKeys().contains("diagnosticRecipe:"));
        assertTrue(skill.signature().contains("update_skill_candidate"));
    }

    @Test
    void candidateWithoutRoutingProfileFailsWithStableCode() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> factory.candidate(Map.of(
                        "reason", "missing routing",
                        "changes", java.util.List.of())));

        assertEquals("SKILL_ROUTING_PROFILE_REQUIRED", error.getMessage());
    }
}
