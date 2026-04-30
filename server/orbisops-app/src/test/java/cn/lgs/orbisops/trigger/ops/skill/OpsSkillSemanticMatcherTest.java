package cn.lgs.orbisops.trigger.ops.skill;

import org.junit.jupiter.api.Test;
import cn.lgs.orbisops.trigger.application.skill.OpsSkillRetrievalHttpClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsSkillSemanticMatcherTest {

    @Test
    void semanticScoresRankMeaningEquivalentSkillAhead() {
        OpsSkillRetrievalHttpClient embeddingModel = mock(OpsSkillRetrievalHttpClient.class);
        when(embeddingModel.configured()).thenReturn(true);
        when(embeddingModel.embed(anyString(),anyBoolean())).thenReturn(
                new float[]{1F, 0F}, new float[]{0.98F, 0.02F}, new float[]{0F, 1F});
        OpsSkillSemanticMatcher matcher = new OpsSkillSemanticMatcher(embeddingModel);

        Map<String, Double> scores = matcher.scores("下单异常诊断", List.of(
                new OpsSkillSemanticMatcher.SkillDocument("order", "hash-order", "订单失败排查"),
                new OpsSkillSemanticMatcher.SkillDocument("redis", "hash-redis", "Redis 容量巡检")));

        assertTrue(scores.get("order") > scores.get("redis"));
        assertEquals(2, scores.size());
    }

    @Test
    void embeddingFailureFallsBackWithoutInventingScores() {
        OpsSkillRetrievalHttpClient embeddingModel = mock(OpsSkillRetrievalHttpClient.class);
        when(embeddingModel.configured()).thenReturn(true);
        when(embeddingModel.embed(anyString(),anyBoolean())).thenThrow(new IllegalStateException("embedding unavailable"));
        OpsSkillSemanticMatcher matcher = new OpsSkillSemanticMatcher(embeddingModel);

        assertTrue(matcher.scores("订单失败", List.of(
                new OpsSkillSemanticMatcher.SkillDocument("order", "hash-order", "订单排查"))).isEmpty());
    }

    @Test
    void disabledSemanticRankingDoesNotResolveEmbeddingModel() {
        OpsSkillRetrievalHttpClient provider = mock(OpsSkillRetrievalHttpClient.class);
        OpsSkillSemanticMatcher matcher = new OpsSkillSemanticMatcher(
                provider,
                new OpsSkillSemanticSettings(false, 64, 2_048));

        assertTrue(matcher.scores("订单失败", List.of(
                new OpsSkillSemanticMatcher.SkillDocument("order", "hash-order", "订单排查"))).isEmpty());
        verify(provider, never()).configured();
    }
}
