package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.skill.SkillCatalogQueryService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.List;
/** Public Skill Evolution similarity facade over typed scoring and catalog matching boundaries. */
@Service
public class OpsSkillSimilarityService {

    private final OpsSkillSimilarityMatchCoordinator matchCoordinator;
    private final OpsSkillFrozenSimilarityMatcher frozenMatcher;

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
        this.frozenMatcher=new OpsSkillFrozenSimilarityMatcher(settings,semanticMatcher);
    }

    public Map<String, Object> bestMatch(String projectId, Map<String, Object> candidate) {
        return matchCoordinator.bestMatch(projectId, candidate);
    }

    public Map<String,Object> bestFrozenMatch(String projectId,Map<String,Object> candidate,List<Map<String,Object>> skills) {
        return frozenMatcher.bestMatch(projectId,candidate,skills);
    }
}
