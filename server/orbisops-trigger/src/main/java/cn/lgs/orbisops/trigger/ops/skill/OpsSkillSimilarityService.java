package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.skill.SkillCatalogQueryService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Public Skill Evolution similarity facade over typed scoring and catalog matching boundaries. */
@Service
public class OpsSkillSimilarityService {

    private final OpsSkillSimilarityMatchCoordinator matchCoordinator;

    public OpsSkillSimilarityService(
            SkillCatalogQueryService catalogQueryService) {
        this(
                catalogQueryService,
                (OpsSkillSemanticMatcher) null,
                OpsSkillSimilaritySettings.legacyConstructorDefaults());
    }

    @Autowired
    public OpsSkillSimilarityService(
            SkillCatalogQueryService catalogQueryService,
            ObjectProvider<OpsSkillSemanticMatcher> semanticMatcherProvider,
            OpsSkillSimilaritySettings settings) {
        this(
                catalogQueryService,
                semanticMatcherProvider == null
                        ? null
                        : semanticMatcherProvider.getIfAvailable(),
                settings);
    }

    OpsSkillSimilarityService(
            SkillCatalogQueryService catalogQueryService,
            OpsSkillSemanticMatcher semanticMatcher,
            OpsSkillSimilaritySettings settings) {
        this.matchCoordinator = new OpsSkillSimilarityMatchCoordinator(
                catalogQueryService,
                settings,
                semanticMatcher);
    }

    public Map<String, Object> bestMatch(
            String projectId,
            Map<String, Object> candidate) {
        return matchCoordinator.bestMatch(projectId, candidate);
    }
}
