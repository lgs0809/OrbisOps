package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillEffectMetrics;
import cn.lgs.orbisops.domain.skill.model.SkillEffectThresholds;
import cn.lgs.orbisops.domain.skill.service.SkillEffectDegradationPolicy;

import java.util.Map;

/** Application facade for Skill effect recording and governance decisions. */
public class SkillEffectMetricApplicationService {

    private final SkillEffectMetricPort metricPort;
    private final SkillEffectDegradationPolicy degradationPolicy;
    private final SkillEffectThresholds thresholds;

    public SkillEffectMetricApplicationService(
            SkillEffectMetricPort metricPort,
            SkillEffectDegradationPolicy degradationPolicy,
            SkillEffectThresholds thresholds) {
        if (metricPort == null) {
            throw new IllegalArgumentException("SKILL_EFFECT_METRIC_PORT_REQUIRED");
        }
        if (thresholds == null) {
            throw new IllegalArgumentException("SKILL_EFFECT_THRESHOLDS_REQUIRED");
        }
        this.metricPort = metricPort;
        this.degradationPolicy = degradationPolicy == null
                ? new SkillEffectDegradationPolicy()
                : degradationPolicy;
        this.thresholds = thresholds;
    }

    public void record(
            String projectId,
            String skillId,
            int version,
            Map<String, Object> outcome) {
        metricPort.record(
                projectId,
                skillId,
                version,
                outcome == null ? Map.of() : outcome);
    }

    public SkillEffectMetrics metrics(
            String projectId,
            String skillId,
            int version) {
        SkillEffectMetrics metrics = metricPort.metrics(
                projectId,
                skillId,
                version);
        return metrics == null ? SkillEffectMetrics.empty() : metrics;
    }

    public boolean enoughSamples(SkillEffectMetrics metrics) {
        return degradationPolicy.enoughSamples(
                metrics == null ? SkillEffectMetrics.empty() : metrics,
                thresholds);
    }

    public boolean shouldRollback(SkillEffectMetrics metrics) {
        return !degradationReason(metrics, SkillEffectMetrics.empty()).isBlank();
    }

    public String degradationReason(
            SkillEffectMetrics candidate,
            SkillEffectMetrics baseline) {
        return degradationPolicy.degradationReason(
                candidate == null ? SkillEffectMetrics.empty() : candidate,
                baseline == null ? SkillEffectMetrics.empty() : baseline,
                thresholds);
    }

    public void applyOutcomeDelta(
            String projectId,
            String skillId,
            int version,
            Map<String, Object> before,
            Map<String, Object> after) {
        metricPort.applyOutcomeDelta(
                projectId,
                skillId,
                version,
                before == null ? Map.of() : before,
                after == null ? Map.of() : after);
    }
}
