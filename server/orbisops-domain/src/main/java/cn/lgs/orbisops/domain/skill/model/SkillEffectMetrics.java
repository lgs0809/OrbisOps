package cn.lgs.orbisops.domain.skill.model;

import java.util.Map;

/** Typed effect counters for one project Skill version. */
public record SkillEffectMetrics(
        int usedRunCount,
        int successfulRunCount,
        int evidenceSufficientCount,
        int toolCallCount,
        int replanCount,
        int blockedToolCallCount,
        int changePackageCreatedCount,
        int changePackageApprovedCount,
        int landingSucceededCount,
        int userNegativeFeedbackCount,
        int needsReplanCount) {

    public static SkillEffectMetrics empty() {
        return new SkillEffectMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public static SkillEffectMetrics from(Map<String, Object> values) {
        Map<String, Object> source = values == null ? Map.of() : values;
        return new SkillEffectMetrics(
                number(source, "used_run_count"),
                number(source, "successful_run_count"),
                number(source, "evidence_sufficient_count"),
                number(source, "tool_call_count"),
                number(source, "replan_count"),
                number(source, "blocked_tool_call_count"),
                number(source, "change_package_created_count"),
                number(source, "change_package_approved_count"),
                number(source, "landing_succeeded_count"),
                number(source, "user_negative_feedback_count"),
                number(source, "needs_replan_count"));
    }

    private static int number(Map<String, Object> source, String key) {
        Object value = source.get(key);
        try {
            return value instanceof Number number
                    ? number.intValue()
                    : Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }
}
