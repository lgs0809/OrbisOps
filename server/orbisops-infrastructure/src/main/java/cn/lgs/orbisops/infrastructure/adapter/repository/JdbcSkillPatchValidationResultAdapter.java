package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.SkillPatchValidationResultPort;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** JDBC implementation for named Skill patch validation results. */
@Repository
public class JdbcSkillPatchValidationResultAdapter
        implements SkillPatchValidationResultPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcSkillPatchValidationResultAdapter(
            @Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Checkpoint> find(String candidateId, String validationType) {
        var rows = jdbcTemplate.query("""
                SELECT status,result_json FROM ai_ops_skill_patch_validation
                WHERE candidate_id=? AND validation_type=?
                """, (rs, row) -> new Checkpoint(rs.getString(1), JSON.parseObject(rs.getString(2))), candidateId, validationType);
        return rows.stream().findFirst();
    }

    @Override
    public void upsert(
            String candidateId,
            String validationType,
            String status,
            double score,
            Map<String, Object> result) {
        jdbcTemplate.update("""
                INSERT INTO ai_ops_skill_patch_validation
                  (validation_id,candidate_id,validation_type,status,score,result_json)
                VALUES (?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                  status=VALUES(status),score=VALUES(score),result_json=VALUES(result_json)
                """,
                "skill-validation-" + UUID.randomUUID(),
                candidateId,
                validationType,
                status,
                score,
                JSON.toJSONString(result == null ? Map.of() : result));
    }
}
