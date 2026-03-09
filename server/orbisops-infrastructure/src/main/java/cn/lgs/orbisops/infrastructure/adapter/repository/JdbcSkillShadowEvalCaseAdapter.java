package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.SkillShadowEvalCasePort;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** JDBC implementation for persisted Skill shadow evaluation cases. */
@Repository
public class JdbcSkillShadowEvalCaseAdapter
        implements SkillShadowEvalCasePort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcSkillShadowEvalCaseAdapter(
            @Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void persist(
            String candidateId,
            Map<String, Object> candidate,
            List<?> cases,
            List<?> evidence,
            Map<String, Object> evaluation) {
        for (Object evalCase : cases == null ? List.of() : cases) {
            String evalCaseId = "skill-eval-" + stableId(
                    candidateId + ":" + JSON.toJSONString(evalCase));
            jdbcTemplate.update("""
                    INSERT INTO ai_ops_skill_eval_case
                      (eval_case_id,project_id,source_run_id,input_json,expected_json,evidence_refs_json,status)
                    VALUES (?,?,?,?,?,?, 'ACTIVE')
                    ON DUPLICATE KEY UPDATE
                      expected_json=VALUES(expected_json),
                      evidence_refs_json=VALUES(evidence_refs_json),
                      status='ACTIVE'
                    """,
                    evalCaseId,
                    candidate.get("project_id"),
                    candidate.get("source_run_id"),
                    JSON.toJSONString(evalCase),
                    JSON.toJSONString(evaluation == null ? Map.of() : evaluation),
                    JSON.toJSONString(evidence == null ? List.of() : evidence));
        }
    }

    private String stableId(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (Exception error) {
            throw new IllegalStateException(
                    "Skill eval case hash 计算失败",
                    error);
        }
    }
}
