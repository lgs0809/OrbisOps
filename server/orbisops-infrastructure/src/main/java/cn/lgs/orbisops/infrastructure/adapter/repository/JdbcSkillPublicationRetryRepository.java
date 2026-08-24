package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Repository
public class JdbcSkillPublicationRetryRepository implements SkillPublicationRetryPort {
    private final JdbcTemplate jdbc;
    public JdbcSkillPublicationRetryRepository(@Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Override public Map<String,Object> status(String project,String candidate) {
        var result=new LinkedHashMap<String,Object>();
        result.put("status","NOT_QUEUED");
        jdbc.queryForList("SELECT status,attempts,next_run_at,last_reason FROM ai_ops_skill_publication_retry WHERE project_id=? AND candidate_id=?",project,candidate)
            .stream().findFirst().ifPresent(result::putAll);
        jdbc.queryForList("SELECT status AS releaseStatus,target_skill_id AS skillId,released_version AS version,reason_code AS reason FROM ai_ops_skill_release WHERE project_id=? AND candidate_id=?",project,candidate)
            .stream().findFirst().ifPresent(result::putAll);
        return result;
    }

    @Override
    public Map<String,Object> enqueue(String project, String candidate) {
        jdbc.update("""
            INSERT INTO ai_ops_skill_publication_retry(candidate_id,project_id,status,next_run_at)
            VALUES(?,?,'PENDING',CURRENT_TIMESTAMP(3))
            ON DUPLICATE KEY UPDATE candidate_id=candidate_id
            """,candidate,project);
        var row=jdbc.queryForMap("SELECT candidate_id,project_id,status,attempts,next_run_at,last_reason FROM ai_ops_skill_publication_retry WHERE candidate_id=?",candidate);
        if(!project.equals(row.get("project_id"))) throw new IllegalArgumentException("SKILL_CANDIDATE_PROJECT_MISMATCH");
        return row;
    }

    @Override
    @Transactional(transactionManager="mysqlTransactionManager")
    public Optional<Claim> claim() {
        var rows=jdbc.queryForList("""
            SELECT candidate_id,attempts FROM ai_ops_skill_publication_retry
            WHERE (status='PENDING' AND next_run_at<=CURRENT_TIMESTAMP(3))
               OR (status='RUNNING' AND lease_until<CURRENT_TIMESTAMP(3))
            ORDER BY next_run_at,candidate_id LIMIT 1 FOR UPDATE SKIP LOCKED
            """);
        if(rows.isEmpty()) return Optional.empty();
        var row=rows.get(0);String candidate=String.valueOf(row.get("candidate_id")), token=UUID.randomUUID().toString();
        jdbc.update("""
            UPDATE ai_ops_skill_publication_retry SET status='RUNNING',attempts=attempts+1,lease_token=?,
                lease_until=DATE_ADD(CURRENT_TIMESTAMP(3),INTERVAL 10 MINUTE) WHERE candidate_id=?
            """,token,candidate);
        return Optional.of(new Claim(candidate,token,((Number)row.get("attempts")).intValue()+1));
    }

    @Override public void requireCurrent(Claim claim) {
        // When called by the publication transaction this locks the lease through the pointer commit.
        if(jdbc.queryForList("""
            SELECT candidate_id FROM ai_ops_skill_publication_retry WHERE candidate_id=? AND status='RUNNING'
                AND lease_token=? AND lease_until>CURRENT_TIMESTAMP(3) FOR UPDATE
            """,claim.candidateId(),claim.token()).isEmpty()) throw new IllegalStateException("SKILL_PUBLICATION_RETRY_LEASE_LOST");
    }

    @Override public void complete(Claim claim, SkillReleaseStartOutcome outcome) {
        if(outcome.disposition()==SkillReleaseStartOutcome.Disposition.DISABLED) {defer(claim,"SKILL_EVOLUTION_DISABLED");return;}
        String status=outcome.released()?"COMPLETED":"REJECTED";
        finish(claim,status,outcome.statusCode());
    }
    @Override public void defer(Claim claim, String reason) { finish(claim,"PENDING",reason); }
    private void finish(Claim claim,String status,String reason) {
        // Full model failures stay in existing protected logs; persist only controlled diagnostics here.
        int delay=Math.min(3600,30*(1<<Math.min(7,Math.max(0,claim.attempt()-1))));
        jdbc.update("""
            UPDATE ai_ops_skill_publication_retry SET status=?,last_reason=?,lease_token=NULL,lease_until=NULL,
                next_run_at=DATE_ADD(CURRENT_TIMESTAMP(3),INTERVAL ? SECOND)
            WHERE candidate_id=? AND status='RUNNING' AND lease_token=? AND lease_until>CURRENT_TIMESTAMP(3)
            """,status,reason,delay,claim.candidateId(),claim.token());
    }
}
