package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.SkillConfusionGraphPort;
import cn.lgs.orbisops.domain.skill.model.SkillConfusionEdge;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class JdbcSkillConfusionGraphAdapter implements SkillConfusionGraphPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcSkillConfusionGraphAdapter(
            @Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbcTemplate) {
        if (jdbcTemplate == null) throw new IllegalArgumentException("SKILL_CONFUSION_JDBC_REQUIRED");
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void replaceEdges(List<SkillConfusionEdge> edges) {
        List<SkillConfusionEdge> safe = edges == null ? List.of() : edges;
        jdbcTemplate.update("DELETE FROM ai_ops_skill_confusion_edge");
        if (safe.isEmpty()) return;
        jdbcTemplate.batchUpdate("""
                INSERT INTO ai_ops_skill_confusion_edge
                  (expected_skill_id, selected_skill_id, false_positive_count,
                   false_negative_count, shadowing_count, average_margin, evaluated_at)
                VALUES (?,?,?,?,?,?,?)
                """, safe, 100, (statement, edge) -> {
            statement.setString(1, edge.expectedSkillId());
            statement.setString(2, edge.selectedSkillId());
            statement.setInt(3, edge.falsePositiveCount());
            statement.setInt(4, edge.falseNegativeCount());
            statement.setInt(5, edge.shadowingCount());
            statement.setDouble(6, edge.averageMargin());
            statement.setTimestamp(7, Timestamp.from(edge.evaluatedAt()));
        });
    }

    @Override
    public List<SkillConfusionEdge> neighbors(String skillId, int limit) {
        return jdbcTemplate.query("""
                        SELECT expected_skill_id, selected_skill_id, false_positive_count,
                               false_negative_count, shadowing_count, average_margin, evaluated_at
                        FROM ai_ops_skill_confusion_edge
                        WHERE expected_skill_id=? OR selected_skill_id=?
                        ORDER BY (false_positive_count + false_negative_count + shadowing_count) DESC,
                                 average_margin ASC,
                                 expected_skill_id ASC,
                                 selected_skill_id ASC
                        LIMIT ?
                        """,
                (row, index) -> new SkillConfusionEdge(
                        row.getString("expected_skill_id"),
                        row.getString("selected_skill_id"),
                        row.getInt("false_positive_count"),
                        row.getInt("false_negative_count"),
                        row.getInt("shadowing_count"),
                        row.getDouble("average_margin"),
                        row.getTimestamp("evaluated_at").toInstant()),
                skillId, skillId, limit);
    }
}
