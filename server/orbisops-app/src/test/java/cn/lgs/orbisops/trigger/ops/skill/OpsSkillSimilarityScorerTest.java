package cn.lgs.orbisops.trigger.ops.skill;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsSkillSimilarityScorerTest {

    private final OpsSkillSimilaritySignatureFactory factory =
            new OpsSkillSimilaritySignatureFactory();

    @Test
    void incompatibleNonGeneralCategoriesFailBeforeSemanticScoring() {
        OpsSkillSemanticMatcher semanticMatcher = mock(OpsSkillSemanticMatcher.class);
        OpsSkillSimilarityScorer scorer =
                new OpsSkillSimilarityScorer(semanticMatcher);

        double score = scorer.score(
                factory.candidate(OpsSkillSimilarityTestFixtures.candidate("OPERATIONS")),
                factory.skill(OpsSkillSimilarityTestFixtures.skill(
                        "document-skill",
                        "DOCUMENT",
                        "ACTIVE")));

        assertEquals(0D, score);
        verify(semanticMatcher, never()).similarity(
                anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void matchingCategoryUsesSemanticDocumentIdentityAndBoundedWeightedScore() {
        OpsSkillSemanticMatcher semanticMatcher = mock(OpsSkillSemanticMatcher.class);
        when(semanticMatcher.similarity(
                anyString(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(0.9D);
        OpsSkillSimilarityScorer scorer =
                new OpsSkillSimilarityScorer(semanticMatcher);

        double score = scorer.score(
                factory.candidate(OpsSkillSimilarityTestFixtures.candidate("OPERATIONS")),
                factory.skill(OpsSkillSimilarityTestFixtures.skill(
                        "order-recovery",
                        "OPERATIONS",
                        "ACTIVE")));

        ArgumentCaptor<OpsSkillSemanticMatcher.SkillDocument> document =
                ArgumentCaptor.forClass(OpsSkillSemanticMatcher.SkillDocument.class);
        verify(semanticMatcher).similarity(anyString(), document.capture());
        assertEquals("order-recovery", document.getValue().id());
        assertEquals("order-recovery-hash", document.getValue().cacheKey());
        assertTrue(score > 0D && score <= 1D);
    }
}
