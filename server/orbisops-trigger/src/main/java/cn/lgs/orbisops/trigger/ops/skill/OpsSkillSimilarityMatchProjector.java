package cn.lgs.orbisops.trigger.ops.skill;

import java.util.LinkedHashMap;
import java.util.Map;

/** Frozen-state policy and stable similarity result Map projection. */
final class OpsSkillSimilarityMatchProjector {

    private final OpsSkillSimilarityTextMetrics metrics;

    OpsSkillSimilarityMatchProjector() {
        this(new OpsSkillSimilarityTextMetrics());
    }

    OpsSkillSimilarityMatchProjector(OpsSkillSimilarityTextMetrics metrics) {
        this.metrics = metrics;
    }

    boolean isFrozen(Map<String, Object> skill) {
        Map<String, Object> effective = skill == null ? Map.of() : skill;
        return "FROZEN".equalsIgnoreCase(metrics.text(effective.get("status")))
                || "FROZEN".equalsIgnoreCase(metrics.text(effective.get("updateMode")))
                || Boolean.TRUE.equals(effective.get("frozen"));
    }

    Map<String, Object> view(Match match, String reason) {
        Map<String, Object> result = new LinkedHashMap<>(match.skill());
        result.put("similarity", Math.round(match.score() * 10_000D) / 10_000D);
        result.put("similarityReason", reason);
        return result;
    }

    record Match(Map<String, Object> skill, double score) {
        Match {
            skill = skill == null ? Map.of() : skill;
        }

        static Match empty() {
            return new Match(Map.of(), 0D);
        }
    }
}
