package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.SkillEffectMetricPort;
import cn.lgs.orbisops.domain.skill.model.SkillEffectMetrics;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** JDBC projection adapter for Skill runtime effect metrics. */
@Repository
public class JdbcSkillEffectMetricAdapter implements SkillEffectMetricPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcSkillEffectMetricAdapter(
            @Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void record(
            String projectId,
            String skillId,
            int version,
            Map<String, Object> outcome) {
        jdbcTemplate.update("""
                INSERT INTO ai_ops_skill_effect_metric
                  (metric_id,project_id,skill_id,skill_version,metric_window,used_run_count,successful_run_count,
                   evidence_sufficient_count,tool_call_count,replan_count,blocked_tool_call_count,change_package_created_count,
                   change_package_approved_count,landing_succeeded_count,user_negative_feedback_count,needs_replan_count)
                VALUES (?,?,?,?, 'LIFETIME',1,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE used_run_count=used_run_count+1,
                  successful_run_count=successful_run_count+VALUES(successful_run_count),
                  evidence_sufficient_count=evidence_sufficient_count+VALUES(evidence_sufficient_count),
                  tool_call_count=tool_call_count+VALUES(tool_call_count),
                  replan_count=replan_count+VALUES(replan_count),
                  blocked_tool_call_count=blocked_tool_call_count+VALUES(blocked_tool_call_count),
                  change_package_created_count=change_package_created_count+VALUES(change_package_created_count),
                  change_package_approved_count=change_package_approved_count+VALUES(change_package_approved_count),
                  landing_succeeded_count=landing_succeeded_count+VALUES(landing_succeeded_count),
                  user_negative_feedback_count=user_negative_feedback_count+VALUES(user_negative_feedback_count),
                  needs_replan_count=needs_replan_count+VALUES(needs_replan_count)
                """,
                "skill-metric-" + UUID.randomUUID(),
                projectId,
                skillId,
                version,
                flag(outcome, "success"),
                flag(outcome, "evidenceSufficient"),
                number(outcome, "toolCallCount"),
                flag(outcome, "replan"),
                number(outcome, "blockedToolCallCount"),
                flag(outcome, "changePackageCreated"),
                flag(outcome, "changePackageApproved"),
                flag(outcome, "landingSucceeded"),
                flag(outcome, "userNegativeFeedback"),
                flag(outcome, "needsReplan"));
    }

    @Override
    public SkillEffectMetrics metrics(
            String projectId,
            String skillId,
            int version) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT *
                FROM ai_ops_skill_effect_metric
                WHERE project_id=? AND skill_id=? AND skill_version=? AND metric_window='LIFETIME'
                """,
                projectId,
                skillId,
                version);
        return rows.isEmpty() ? SkillEffectMetrics.empty() : SkillEffectMetrics.from(rows.get(0));
    }

    @Override
    public void applyOutcomeDelta(
            String projectId,
            String skillId,
            int version,
            Map<String, Object> before,
            Map<String, Object> after) {
        int approved = transition(before, after, "changePackageApproved");
        int landed = transition(before, after, "landingSucceeded");
        int needsReplan = transition(before, after, "needsReplan");
        int negative = transition(before, after, "userNegativeFeedback");
        int created = transition(before, after, "changePackageCreated");
        if (approved == 0
                && landed == 0
                && needsReplan == 0
                && negative == 0
                && created == 0) {
            return;
        }
        int updated = jdbcTemplate.update("""
                UPDATE ai_ops_skill_effect_metric
                SET change_package_created_count=GREATEST(0,change_package_created_count+?),
                    change_package_approved_count=GREATEST(0,change_package_approved_count+?),
                    landing_succeeded_count=GREATEST(0,landing_succeeded_count+?),
                    user_negative_feedback_count=GREATEST(0,user_negative_feedback_count+?),
                    needs_replan_count=GREATEST(0,needs_replan_count+?)
                WHERE project_id=? AND skill_id=? AND skill_version=? AND metric_window='LIFETIME'
                """,
                created,
                approved,
                landed,
                negative,
                needsReplan,
                projectId,
                skillId,
                version);
        if (updated != 1) {
            throw new IllegalStateException(
                    "SKILL_METRIC_RECONCILIATION_MISSING:"
                            + skillId
                            + "@"
                            + version);
        }
    }

    private int transition(
            Map<String, Object> before,
            Map<String, Object> after,
            String key) {
        return flag(after, key) - flag(before, key);
    }

    private int flag(Map<String, Object> outcome, String key) {
        return Boolean.TRUE.equals(outcome.get(key)) ? 1 : 0;
    }

    private int number(Map<String, Object> outcome, String key) {
        Object value = outcome.get(key);
        try {
            return value instanceof Number number
                    ? number.intValue()
                    : Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }
}
