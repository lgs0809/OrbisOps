package cn.lgs.orbisops.trigger.ops.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * Optional semantic scoring for the bounded Skill candidate set. Runtime safety
 * never depends on this service: unavailable embeddings fall back to the
 * deterministic lexical score while catalog and full-content limits remain in force.
 */
@Slf4j
@Service
public class OpsSkillSemanticMatcher {

    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final OpsSkillSemanticSettings settings;
    private final OpsSkillEmbeddingScoreCoordinator scoreCoordinator;

    public OpsSkillSemanticMatcher(
            @Qualifier("ragEmbeddingModel")
            ObjectProvider<EmbeddingModel> embeddingModelProvider) {
        this(embeddingModelProvider, OpsSkillSemanticSettings.defaults());
    }

    @Autowired
    public OpsSkillSemanticMatcher(
            @Qualifier("ragEmbeddingModel")
            ObjectProvider<EmbeddingModel> embeddingModelProvider,
            OpsSkillSemanticSettings settings) {
        this.embeddingModelProvider = embeddingModelProvider;
        this.settings = settings == null
                ? OpsSkillSemanticSettings.defaults()
                : settings;
        this.scoreCoordinator = new OpsSkillEmbeddingScoreCoordinator(this.settings);
    }

    public Map<String, Double> scores(
            String query,
            List<SkillDocument> documents) {
        if (!settings.semanticEnabled()
                || !StringUtils.hasText(query)
                || documents == null
                || documents.isEmpty()) {
            return Map.of();
        }
        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        if (model == null) {
            return Map.of();
        }
        try {
            return scoreCoordinator.scores(model, query, documents);
        } catch (RuntimeException error) {
            log.warn(
                    "Skill embedding 语义排序不可用，退回有界词法排序 reason={}",
                    error.getMessage());
            return Map.of();
        }
    }

    public double similarity(String left, SkillDocument right) {
        return scores(left, List.of(right)).getOrDefault(right.id(), 0D);
    }

    public record SkillDocument(String id, String cacheKey, String text) { }
}
