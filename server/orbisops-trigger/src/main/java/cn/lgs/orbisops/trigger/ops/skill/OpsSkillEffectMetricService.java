package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.skill.SkillEffectMetricApplicationService;
import cn.lgs.orbisops.domain.skill.model.SkillEffectMetrics;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Compatibility facade over the Skill Effect Metric application use case. */
@Service
public class OpsSkillEffectMetricService {

    private final SkillEffectMetricApplicationService applicationService;

    public OpsSkillEffectMetricService(
            SkillEffectMetricApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    public void record(
            String projectId,
            String skillId,
            int version,
            Map<String, Object> outcome) {
        applicationService.record(projectId, skillId, version, outcome);
    }

    public Map<String, Object> metrics(
            String projectId,
            String skillId,
            int version) {
        return view(applicationService.metrics(projectId, skillId, version));
    }

    public boolean enoughSamples(Map<String, Object> metrics) {
        return applicationService.enoughSamples(SkillEffectMetrics.from(metrics));
    }

    public boolean shouldRollback(Map<String, Object> metrics) {
        return applicationService.shouldRollback(SkillEffectMetrics.from(metrics));
    }

    public void applyOutcomeDelta(
            String projectId,
            String skillId,
            int version,
            Map<String, Object> before,
            Map<String, Object> after) {
        applicationService.applyOutcomeDelta(
                projectId,
                skillId,
                version,
                before,
                after);
    }

    public String degradationReason(
            Map<String, Object> candidate,
            Map<String, Object> baseline) {
        return applicationService.degradationReason(
                SkillEffectMetrics.from(candidate),
                SkillEffectMetrics.from(baseline));
    }

    private Map<String, Object> view(SkillEffectMetrics metrics) {
        SkillEffectMetrics safe = metrics == null ? SkillEffectMetrics.empty() : metrics;
        return Map.ofEntries(
                Map.entry("used_run_count", safe.usedRunCount()),
                Map.entry("successful_run_count", safe.successfulRunCount()),
                Map.entry("evidence_sufficient_count", safe.evidenceSufficientCount()),
                Map.entry("tool_call_count", safe.toolCallCount()),
                Map.entry("replan_count", safe.replanCount()),
                Map.entry("blocked_tool_call_count", safe.blockedToolCallCount()),
                Map.entry("change_package_created_count", safe.changePackageCreatedCount()),
                Map.entry("change_package_approved_count", safe.changePackageApprovedCount()),
                Map.entry("landing_succeeded_count", safe.landingSucceededCount()),
                Map.entry("user_negative_feedback_count", safe.userNegativeFeedbackCount()),
                Map.entry("needs_replan_count", safe.needsReplanCount()));
    }
}
