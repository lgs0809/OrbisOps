package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.SkillLifecyclePort;
import cn.lgs.orbisops.domain.skill.model.SkillLifecycleDecision;
import cn.lgs.orbisops.domain.skill.model.SkillLifecycleProposal;
import cn.lgs.orbisops.domain.skill.model.SkillLineageEdge;
import cn.lgs.orbisops.domain.skill.model.SkillLineageRelation;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class JdbcSkillLifecycleAdapter implements SkillLifecyclePort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcSkillLifecycleAdapter(
            @Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbcTemplate) {
        if (jdbcTemplate == null) throw new IllegalArgumentException("SKILL_LIFECYCLE_JDBC_REQUIRED");
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveProposal(SkillLifecycleProposal proposal) {
        if (proposal == null) throw new IllegalArgumentException("SKILL_LIFECYCLE_PROPOSAL_REQUIRED");
        jdbcTemplate.update("""
                INSERT INTO ai_ops_skill_lifecycle_proposal
                  (proposal_id, operation, sources_json, target_skill_ids_json, reason,
                   evidence_ids_json, behavior_evaluation_id, behavior_replay_passed, proposed_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE proposal_id=proposal_id
                """,
                proposal.proposalId(), proposal.operation().name(),
                JSON.toJSONString(proposal.sources()),
                JSON.toJSONString(proposal.targetSkillIds()), proposal.reason(),
                JSON.toJSONString(proposal.evidenceIds()), proposal.behaviorEvaluationId(),
                proposal.behaviorReplayPassed(), Timestamp.from(proposal.proposedAt()));
    }

    @Override
    public void saveDecision(SkillLifecycleDecision decision) {
        if (decision == null) throw new IllegalArgumentException("SKILL_LIFECYCLE_DECISION_REQUIRED");
        jdbcTemplate.update("""
                INSERT INTO ai_ops_skill_lifecycle_decision
                  (proposal_id, approved, target_retention_state, reason_codes_json)
                VALUES (?,?,?,?)
                ON DUPLICATE KEY UPDATE
                  approved=VALUES(approved),
                  target_retention_state=VALUES(target_retention_state),
                  reason_codes_json=VALUES(reason_codes_json)
                """,
                decision.proposalId(), decision.approved(),
                decision.targetRetentionState() == null
                        ? null : decision.targetRetentionState().name(),
                JSON.toJSONString(decision.reasonCodes()));
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void saveLineage(List<SkillLineageEdge> edges) {
        List<SkillLineageEdge> safe = edges == null ? List.of() : edges;
        if (safe.isEmpty()) return;
        jdbcTemplate.batchUpdate("""
                INSERT INTO ai_ops_skill_lineage_edge
                  (proposal_id, source_reference, target_skill_id, relation_type, created_at)
                VALUES (?,?,?,?,?)
                ON DUPLICATE KEY UPDATE proposal_id=proposal_id
                """, safe, 100, (statement, edge) -> {
            statement.setString(1, edge.proposalId());
            statement.setString(2, edge.sourceReference());
            statement.setString(3, edge.targetSkillId());
            statement.setString(4, edge.relation().name());
            statement.setTimestamp(5, Timestamp.from(edge.createdAt()));
        });
    }

    @Override
    public List<SkillLineageEdge> lineage(String skillId, int limit) {
        return jdbcTemplate.query("""
                        SELECT proposal_id, source_reference, target_skill_id, relation_type, created_at
                        FROM ai_ops_skill_lineage_edge
                        WHERE source_reference LIKE ? OR target_skill_id=?
                        ORDER BY created_at DESC, proposal_id ASC,
                                 source_reference ASC, target_skill_id ASC
                        LIMIT ?
                        """,
                (row, index) -> new SkillLineageEdge(
                        row.getString("proposal_id"),
                        row.getString("source_reference"),
                        row.getString("target_skill_id"),
                        SkillLineageRelation.valueOf(row.getString("relation_type")),
                        row.getTimestamp("created_at").toInstant()),
                skillId + "@%", skillId, limit);
    }
}
