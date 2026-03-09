package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillEffectMetrics;

import java.util.Map;

/** Persistence port for Skill runtime effect aggregates. */
public interface SkillEffectMetricPort {

    void record(String projectId,
                String skillId,
                int version,
                Map<String, Object> outcome);

    SkillEffectMetrics metrics(String projectId, String skillId, int version);

    void applyOutcomeDelta(String projectId,
                           String skillId,
                           int version,
                           Map<String, Object> before,
                           Map<String, Object> after);
}
