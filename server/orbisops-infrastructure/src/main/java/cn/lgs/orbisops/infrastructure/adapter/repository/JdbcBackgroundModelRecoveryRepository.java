package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.episode.BackgroundModelRecoveryPort;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.skill.service.SkillEvolutionJobPolicy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.util.*;

@Repository
public class JdbcBackgroundModelRecoveryRepository implements BackgroundModelRecoveryPort {
    private final JdbcTemplate jdbc;
    public JdbcBackgroundModelRecoveryRepository(@Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbc) {this.jdbc=jdbc;}
    private record Shape(String table,String key,String from,String project,String session,String status,String attempts,String error,String hash,String variant) { }
    private Shape shape(Family family) {
        String session=" JOIN ai_ops_chat_session s ON s.session_id=t.session_id AND s.project_id=t.project_id ";
        return switch(family) {
            case EPISODE_TURN -> new Shape("ai_ops_task_episode_turn","id","ai_ops_task_episode_turn t"+session,"t.project_id","t.session_id","RETRY_EXHAUSTED","attempts","last_error","input_hash","'TURN'");
            case EPISODE_CONSOLIDATION -> new Shape("ai_ops_episode_consolidation_job","id","ai_ops_episode_consolidation_job t JOIN ai_ops_task_episode e ON e.episode_id=t.episode_id JOIN ai_ops_chat_session s ON s.session_id=e.session_id AND s.project_id=e.project_id ","e.project_id","e.session_id","RETRY_EXHAUSTED","attempts","last_error","input_hash","'CONSOLIDATION'");
            case MEMORY -> new Shape("ai_ops_memory_post_processing","id","ai_ops_memory_post_processing t JOIN ai_ops_chat_session s ON s.session_id=t.session_id JOIN ai_ops_chat_message m ON m.session_id=t.session_id AND m.message_seq=t.message_seq AND COALESCE(m.project_id,'')=COALESCE(s.project_id,'') ","COALESCE(s.project_id,'')","t.session_id","FAILED","attempts","last_error","model_input_hash","t.task_type");
            case SKILL_REVIEW -> new Shape("ai_ops_skill_canary_review","review_id","ai_ops_skill_canary_review t JOIN ai_ops_task_episode e ON e.episode_id=t.episode_id AND e.project_id=t.project_id JOIN ai_ops_chat_session s ON s.session_id=e.session_id AND s.project_id=e.project_id JOIN ai_ops_skill_release r ON r.release_id=t.release_id AND r.project_id=t.project_id AND r.status IN ('CANARY','ACTIVE') ","t.project_id","e.session_id","EXHAUSTED","attempt_count","reason_code","input_hash","'REVIEW'");
            case SKILL_EVOLUTION -> new Shape("ai_ops_skill_evolution_job","job_id","ai_ops_skill_evolution_job t"+session+" JOIN ai_ops_skill_evolution_job_state st ON st.job_id=t.job_id JOIN ai_ops_skill_evolution_source src ON src.source_id=st.source_id AND src.project_id=t.project_id AND src.session_id=t.session_id ","t.project_id","t.session_id","FAILED","attempts","last_error","src.input_hash","st.source_id");
        };
    }
    @Override public List<Candidate> candidates(Family family,String project,String taskId,String after,int limit) {
        var x=shape(family);var args=new ArrayList<Object>();args.add(x.status());
        String where="t.status=? AND s.user_id<>''";
        if(!project.isBlank()){where+=" AND "+x.project()+"=?";args.add(project);}
        if(!taskId.isBlank()){where+=" AND t."+x.key()+"=?";args.add(taskId);}
        if(!after.isBlank()){where+=" AND t."+x.key()+">?";args.add(after);}
        args.add(Math.max(1,Math.min(limit,20)));
        return jdbc.query("SELECT t."+x.key()+" AS task_id,"+x.project()+" AS project_id,"+x.session()+" AS session_id,s.user_id AS owner_id,t.status,t."+x.attempts()+" AS attempts,t."+x.error()+" AS error,"+x.hash()+" AS input_hash,"+x.variant()+" AS variant FROM "+x.from()+" WHERE "+where+" ORDER BY t."+x.key()+" LIMIT ?",
                (r,n)->new Candidate(family,r.getString("task_id"),r.getString("project_id"),r.getString("session_id"),r.getString("owner_id"),r.getString("status"),r.getInt("attempts"),Objects.toString(r.getString("error"),""),Objects.toString(r.getString("input_hash"),""),r.getString("variant")),args.toArray());
    }
    @Override @Transactional(transactionManager="mysqlTransactionManager")
    public boolean restore(Candidate candidate,long due,String actor,Runnable authorize) {
        var x=shape(candidate.family());
        String nextColumn=switch(candidate.family()) {
            case EPISODE_TURN,EPISODE_CONSOLIDATION,MEMORY -> "next_attempt_ms";
            case SKILL_REVIEW -> "next_attempt_at";
            case SKILL_EVOLUTION -> "next_run_at";
        };
        var locked=jdbc.queryForList("SELECT "+nextColumn+" AS prior_due FROM "+x.table()+" WHERE "+x.key()+"=? AND status=? FOR UPDATE",candidate.taskId(),candidate.status());
        if(locked.isEmpty())return false;
        Object previousDue=locked.get(0).get("prior_due");
        long priorMillis=previousDue instanceof java.util.Date date ? date.getTime() : previousDue instanceof Number number ? number.longValue() : 0;
        // Recovery must not shorten a provider cooldown that was persisted by the old worker.
        due=Math.max(due,priorMillis);
        var current=candidates(candidate.family(),candidate.projectId(),candidate.taskId(),"",1);
        if(current.size()!=1 || !current.get(0).equals(candidate))return false;
        authorize.run();
        if(!valid(candidate,x))return false;
        String id=CanonicalObjectHasher.sha256(Map.of("family",candidate.family().name(),"task",candidate.taskId(),"attempts",candidate.attempts(),"error",candidate.error(),"input",candidate.inputHash()));
        if(jdbc.update("""
                INSERT IGNORE INTO ai_ops_background_model_recovery_audit(recovery_id,family,task_id,project_id,actor,prior_status,prior_attempts,prior_error,next_attempt_ms)
                VALUES (?,?,?,?,?,?,?,?,?)
                """,id,candidate.family().name(),candidate.taskId(),candidate.projectId(),actor,candidate.status(),candidate.attempts(),candidate.error().substring(0,Math.min(200,candidate.error().length())),due)!=1)return false;
        switch(candidate.family()) {
            case EPISODE_TURN, EPISODE_CONSOLIDATION -> jdbc.update("UPDATE "+x.table()+" SET status='PENDING_RETRY',next_attempt_ms=?,lease_until_ms=0 WHERE "+x.key()+"=?",due,candidate.taskId());
            case MEMORY -> jdbc.update("UPDATE "+x.table()+" SET status='RETRY_WAIT',next_attempt_ms=?,lease_until_ms=0 WHERE id=?",due,candidate.taskId());
            case SKILL_REVIEW -> jdbc.update("UPDATE "+x.table()+" SET status='RETRY_WAIT',next_attempt_at=?,lease_until=0 WHERE review_id=?",due,candidate.taskId());
            case SKILL_EVOLUTION -> {
                jdbc.update("UPDATE "+x.table()+" SET status='PENDING',next_run_at=? WHERE job_id=?",new Timestamp(due),candidate.taskId());
                jdbc.update("UPDATE ai_ops_skill_evolution_job_state SET lease_token='',lease_until_ms=0,epoch=epoch+1 WHERE job_id=?",candidate.taskId());
            }
        }
        return true;
    }
    private boolean valid(Candidate c,Shape x) {
        if(c.family()==Family.SKILL_EVOLUTION) {
            if(SkillEvolutionJobPolicy.sourceBlocked(c.error()))return false;
            try {
                var run=jdbc.queryForObject("SELECT run_id FROM ai_ops_skill_evolution_job WHERE job_id=?",String.class,c.taskId());
                new JdbcSkillEvolutionSourceReader(jdbc).loadAccepted(c.projectId(),run,c.variant());return true;
            } catch(IllegalStateException | IllegalArgumentException invalid){return false;}
        }
        if(c.family()==Family.MEMORY && "SEMANTIC".equals(c.variant())) {
            if(c.inputHash().isBlank())return false; // Old stopped rows without a frozen source cannot invent one during recovery.
            var retained=jdbc.queryForList("SELECT m.* FROM ai_ops_memory_post_processing j JOIN ai_ops_chat_message m ON m.session_id=j.session_id AND m.message_seq=j.message_seq WHERE j.id=?",c.taskId());
            if(retained.size()!=1)return false;
            var row=retained.get(0);
            var metadata=com.alibaba.fastjson.JSON.parseObject(Objects.toString(row.get("metadata"),"{}"));
            var source=new cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot(c.sessionId(),Objects.toString(row.get("user_id"),""),Objects.toString(row.get("role"),""),Objects.toString(row.get("content"),""),"",metadata);
            return cn.lgs.orbisops.domain.memory.service.MemoryProcessingSourcePolicy.active(source)
                    && c.inputHash().equals(cn.lgs.orbisops.domain.memory.service.MemoryProcessingSourcePolicy.hash(source));
        }
        if(c.family()==Family.MEMORY && !"COMPRESSION_MODEL".equals(c.variant()))return true;
        String column=c.family()==Family.MEMORY?"model_input_json":"input_json";
        String raw=jdbc.queryForObject("SELECT "+column+" FROM "+x.table()+" WHERE "+x.key()+"=?",String.class,c.taskId());
        if(raw==null || raw.isBlank() || !CanonicalObjectHasher.sha256Text(raw).equals(c.inputHash()))return false;
        if(c.family()==Family.MEMORY) {
            try {
                var input=cn.lgs.orbisops.domain.memory.model.MemorySummaryModelInput.parse(raw);
                var states=jdbc.queryForList("SELECT project_id,user_id FROM ai_ops_conversation_memory_state WHERE session_id=?",c.sessionId());
                if(states.isEmpty())return false;
                input.requireScope(c.sessionId(),Objects.toString(states.get(0).get("project_id"),""),Objects.toString(states.get(0).get("user_id"),""));
                return true;
            } catch(cn.lgs.orbisops.domain.memory.model.MemoryModelInputInvalidException invalid){return false;}
        }
        if(c.family()==Family.SKILL_REVIEW) {
            String release=jdbc.queryForObject("SELECT release_id FROM "+x.table()+" WHERE review_id=?",String.class,c.taskId());
            String episode=jdbc.queryForObject("SELECT episode_id FROM "+x.table()+" WHERE review_id=?",String.class,c.taskId());
            return new JdbcSkillCanaryReviewInput(jdbc).read(c.projectId(),episode,release).map(raw::equals).orElse(false);
        }
        return true;
    }
}
