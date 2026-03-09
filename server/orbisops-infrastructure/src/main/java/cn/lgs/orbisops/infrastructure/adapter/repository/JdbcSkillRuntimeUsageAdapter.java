package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.SkillRuntimeUsageCommand;
import cn.lgs.orbisops.application.skill.SkillRuntimeUsagePort;
import cn.lgs.orbisops.application.skill.SkillRuntimeUsageRecord;
import cn.lgs.orbisops.application.skill.SkillRuntimeUsageReference;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** JDBC implementation of the runtime Skill usage ledger. */
@Repository
public class JdbcSkillRuntimeUsageAdapter implements SkillRuntimeUsagePort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcSkillRuntimeUsageAdapter(
            @Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean insert(
            SkillRuntimeUsageCommand command,
            SkillRuntimeUsageReference reference) {
        return jdbcTemplate.update("""
                INSERT IGNORE INTO ai_ops_skill_runtime_usage
                  (usage_id,project_id,agent_id,run_id,context_bundle_hash,skill_id,skill_version,skill_hash,used_at_node,outcome_json)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """,
                "skill-usage-" + UUID.randomUUID(),
                command.projectId(),
                command.agentId(),
                command.runId(),
                command.contextBundleHash(),
                reference.skillId(),
                reference.version(),
                reference.skillHash(),
                reference.usedAtNode(),
                JSON.toJSONString(command.outcome())) == 1;
    }

    @Override
    public List<SkillRuntimeUsageRecord> lockForRun(
            String projectId,
            String runId) {
        return jdbcTemplate.query("""
                        SELECT id, skill_id, skill_version, outcome_json
                        FROM ai_ops_skill_runtime_usage
                        WHERE project_id=? AND run_id=?
                        FOR UPDATE
                        """,
                (rs, rowNum) -> {
                    String outcomeJson = text(rs.getString("outcome_json"));
                    return new SkillRuntimeUsageRecord(
                            rs.getLong("id"),
                            text(rs.getString("skill_id")),
                            rs.getInt("skill_version"),
                            parseOutcome(outcomeJson),
                            outcomeJson);
                },
                projectId,
                runId);
    }

    @Override
    public boolean compareAndSetOutcome(
            long id,
            String persistenceToken,
            Map<String, Object> outcome) {
        return jdbcTemplate.update("""
                        UPDATE ai_ops_skill_runtime_usage
                        SET outcome_json=?
                        WHERE id=? AND outcome_json=?
                        """,
                JSON.toJSONString(outcome),
                id,
                persistenceToken) == 1;
    }

    @Override
    public List<Map<String, Object>> listForRun(
            String projectId,
            String runId) {
        return jdbcTemplate.queryForList("""
                        SELECT usage_id,project_id,agent_id,run_id,context_bundle_hash,skill_id,skill_version,
                               skill_hash,used_at_node,outcome_json,created_at
                        FROM ai_ops_skill_runtime_usage
                        WHERE project_id=? AND run_id=? ORDER BY id ASC
                        """,
                projectId,
                runId).stream()
                .map(row -> {
                    Map<String, Object> view = new LinkedHashMap<>(row);
                    view.put("outcome", parseOutcome(row.get("outcome_json")));
                    view.remove("outcome_json");
                    return view;
                })
                .toList();
    }

    private Map<String, Object> parseOutcome(Object value) {
        if (value instanceof Map<?, ?> map) {
            return copyNonNull(map);
        }
        String json = text(value);
        if (json.isBlank() || "null".equalsIgnoreCase(json)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = JSON.parseObject(json);
            return parsed == null
                    ? new LinkedHashMap<>()
                    : copyNonNull(parsed);
        } catch (RuntimeException error) {
            throw new IllegalStateException(
                    "SKILL_USAGE_OUTCOME_INVALID_JSON",
                    error);
        }
    }

    private Map<String, Object> copyNonNull(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> {
            if (key != null && item != null) {
                result.put(String.valueOf(key), item);
            }
        });
        return result;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
