package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.analysis.adapter.repository.IAnalysisFeedbackRepository;
import cn.lgs.orbisops.domain.analysis.model.AnalysisFeedbackType;
import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskFeedback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Repository
public class JdbcAnalysisFeedbackRepository implements IAnalysisFeedbackRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAnalysisFeedbackRepository(
            @Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public AnalysisTaskFeedback save(AnalysisTaskFeedback feedback) {
        jdbcTemplate.update("""
                INSERT INTO ai_ops_analysis_feedback
                  (feedback_id,project_id,run_id,feedback_type,comment_text,created_by)
                VALUES (?,?,?,?,?,?)
                """,
                feedback.feedbackId(),
                feedback.projectId(),
                feedback.runId(),
                feedback.feedbackType().name(),
                feedback.comment(),
                feedback.actor());
        return feedback;
    }

    @Override
    public List<AnalysisTaskFeedback> list(String projectId, String runId) {
        return jdbcTemplate.queryForList("""
                        SELECT feedback_id,project_id,run_id,feedback_type,comment_text,created_by,created_at
                        FROM ai_ops_analysis_feedback
                        WHERE project_id=? AND run_id=? ORDER BY id DESC
                        """, projectId, runId)
                .stream()
                .map(this::feedback)
                .toList();
    }

    private AnalysisTaskFeedback feedback(Map<String, Object> row) {
        return new AnalysisTaskFeedback(
                text(row.get("feedback_id")),
                text(row.get("project_id")),
                text(row.get("run_id")),
                AnalysisFeedbackType.require(text(row.get("feedback_type"))),
                text(row.get("comment_text")),
                text(row.get("created_by")),
                instant(row.get("created_at")));
    }

    private Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.util.Date date) return date.toInstant();
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        try {
            return Instant.parse(text(value));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
