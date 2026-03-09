package cn.lgs.orbisops.trigger.ops.skill;

import org.springframework.ai.embedding.EmbeddingModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Batch embedding, cache population, and semantic score projection. */
final class OpsSkillEmbeddingScoreCoordinator {

    private final OpsSkillSemanticSettings settings;
    private final OpsSkillSemanticPolicy policy;
    private final OpsSkillEmbeddingCache cache;

    OpsSkillEmbeddingScoreCoordinator(OpsSkillSemanticSettings settings) {
        this(
                settings,
                new OpsSkillSemanticPolicy(),
                new OpsSkillEmbeddingCache(effective(settings).cacheSize()));
    }

    OpsSkillEmbeddingScoreCoordinator(
            OpsSkillSemanticSettings settings,
            OpsSkillSemanticPolicy policy,
            OpsSkillEmbeddingCache cache) {
        this.settings = effective(settings);
        this.policy = policy;
        this.cache = cache;
    }

    Map<String, Double> scores(
            EmbeddingModel model,
            String query,
            List<OpsSkillSemanticMatcher.SkillDocument> documents) {
        List<OpsSkillSemanticMatcher.SkillDocument> bounded =
                policy.bounded(documents, settings);
        if (bounded.isEmpty()) {
            return Map.of();
        }
        List<String> missingKeys = new ArrayList<>();
        List<String> batch = new ArrayList<>();
        batch.add(query);
        for (OpsSkillSemanticMatcher.SkillDocument document : bounded) {
            String cacheKey = policy.cacheKey(document);
            if (!cache.contains(cacheKey)) {
                missingKeys.add(cacheKey);
                batch.add(document.text());
            }
        }
        List<float[]> vectors = model.embed(batch);
        if (vectors == null
                || vectors.size() != batch.size()
                || vectors.get(0) == null) {
            return Map.of();
        }
        float[] queryVector = vectors.get(0);
        for (int index = 0; index < missingKeys.size(); index++) {
            cache.put(missingKeys.get(index), vectors.get(index + 1));
        }
        Map<String, Double> result = new LinkedHashMap<>();
        for (OpsSkillSemanticMatcher.SkillDocument document : bounded) {
            float[] vector = cache.get(policy.cacheKey(document));
            if (vector != null) {
                result.put(document.id(), policy.cosine(queryVector, vector));
            }
        }
        return Map.copyOf(result);
    }

    private static OpsSkillSemanticSettings effective(
            OpsSkillSemanticSettings settings) {
        return settings == null ? OpsSkillSemanticSettings.defaults() : settings;
    }
}
