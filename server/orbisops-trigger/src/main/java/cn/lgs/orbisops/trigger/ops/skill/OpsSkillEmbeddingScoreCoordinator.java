package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.trigger.application.skill.OpsSkillRetrievalHttpClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pinned local embedding, revision-scoped cache population, and semantic score projection. */
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
            OpsSkillRetrievalHttpClient model,
            String query,
            List<OpsSkillSemanticMatcher.SkillDocument> documents) {
        List<OpsSkillSemanticMatcher.SkillDocument> bounded =
                policy.bounded(documents, settings);
        if (bounded.isEmpty()) {
            return Map.of();
        }
        // Query/document instructions differ. Never reuse vectors across model revisions.
        String identity = model.modelIdentity() + ":";
        float[] queryVector = model.embed(query, true);
        for (OpsSkillSemanticMatcher.SkillDocument document : bounded) {
            String cacheKey = identity + policy.cacheKey(document);
            if (!cache.contains(cacheKey)) cache.put(cacheKey, model.embed(document.text(), false));
        }
        Map<String, Double> result = new LinkedHashMap<>();
        for (OpsSkillSemanticMatcher.SkillDocument document : bounded) {
            float[] vector = cache.get(identity + policy.cacheKey(document));
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
