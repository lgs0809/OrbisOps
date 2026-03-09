package cn.lgs.orbisops.trigger.ops.skill;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsSkillEmbeddingScoreCoordinatorTest {

    @Test
    void reusesDocumentEmbeddingsAndOnlyEmbedsTheNextQuery() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embed(anyList()))
                .thenReturn(List.of(
                        new float[]{1F, 0F},
                        new float[]{1F, 0F},
                        new float[]{0F, 1F}))
                .thenReturn(List.of(new float[]{0F, 1F}));
        OpsSkillEmbeddingScoreCoordinator coordinator =
                new OpsSkillEmbeddingScoreCoordinator(
                        new OpsSkillSemanticSettings(true, 2, 128));
        List<OpsSkillSemanticMatcher.SkillDocument> documents = List.of(
                new OpsSkillSemanticMatcher.SkillDocument("order", "order-v1", "订单失败排查"),
                new OpsSkillSemanticMatcher.SkillDocument("redis", "redis-v1", "Redis 容量巡检"));

        Map<String, Double> first = coordinator.scores(model, "订单失败", documents);
        Map<String, Double> second = coordinator.scores(model, "Redis 容量", documents);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> batches = ArgumentCaptor.forClass(List.class);
        verify(model, times(2)).embed(batches.capture());
        assertEquals(3, batches.getAllValues().get(0).size());
        assertEquals(1, batches.getAllValues().get(1).size());
        assertTrue(first.get("order") > first.get("redis"));
        assertTrue(second.get("redis") > second.get("order"));
    }
}
