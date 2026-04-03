package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.SkillMaintenancePort;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class JdbcSkillMaintenanceRepository implements SkillMaintenancePort {
    private final JdbcTemplate jdbc;
    public JdbcSkillMaintenanceRepository(@Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbc) {this.jdbc=jdbc;}

    @Override public List<Subject> scan(long afterId,int limit) {
        // A recorded runtime use is a conservative proxy: it suppresses an inactivity warning, never proves success.
        return jdbc.query("""
            SELECT s.*, (SELECT MAX(u.created_at) FROM ai_ops_skill_runtime_usage u
                WHERE u.project_id=s.project_id AND u.skill_id=s.skill_id) AS last_use_at,
              (SELECT COUNT(*) FROM ai_ops_skill_version v JOIN ai_ops_skill_patch_candidate c ON c.candidate_id=v.evolution_job_id
                WHERE v.scope='PROJECT' AND v.project_id=s.project_id AND v.skill_id=s.skill_id AND v.version<=s.current_version
                  AND c.patch_type IN ('UPDATE_ROUTING_RULE','UPDATE_DIAGNOSTIC_RECIPE','UPDATE_EVIDENCE_CRITERIA','UPDATE_NEGATIVE_RULE')) AS patches,
              (SELECT COALESCE(MAX(m.patch_count),0) FROM ai_ops_skill_maintenance m
                WHERE m.project_id=s.project_id AND m.skill_id=s.skill_id AND m.kind='COMPRESS_CHECK'
                  AND m.base_version<=s.current_version AND m.status IN ('NO_CHANGE','REVIEW_REQUIRED','PENDING_INDEX','ACTIVE')) AS checked_patches
            FROM ai_ops_skill s WHERE s.id>? AND s.scope='PROJECT' AND s.status='ENABLED'
            ORDER BY s.id LIMIT ?
            """,(rs,n)->new Subject(rs.getLong("id"),rs.getString("project_id"),rs.getString("skill_id"),
                rs.getInt("current_version"),rs.getString("current_skill_hash"),rs.getString("current_package_hash"),
                Objects.toString(rs.getString("content"),""),rs.getTimestamp("create_time").toInstant(),
                instant(rs.getTimestamp("last_use_at")),rs.getInt("patches"),rs.getInt("checked_patches"),
                "AUTO".equals(rs.getString("update_mode")) && rs.getBoolean("auto_update_enabled")
                    && "NONE".equals(rs.getString("lock_type"))),afterId,Math.min(100,Math.max(1,limit)));
    }

    @Override public void enqueue(Subject s,String kind,int tokens,String tokenizer,String reason) {
        if(!Set.of("COMPRESS_CHECK","INACTIVITY_REVIEW").contains(kind)) throw new IllegalArgumentException("SKILL_MAINTENANCE_KIND_INVALID");
        String id=CanonicalObjectHasher.sha256Text(s.projectId()+"\n"+s.skillId()+"\n"+s.version()+"\n"+s.skillHash()+"\n"+kind
            +("COMPRESS_CHECK".equals(kind)?"\nsemantic-maintenance-v1":"")
            +("INACTIVITY_REVIEW".equals(kind)?"\n"+s.lastUseAt():""));
        jdbc.update("""
            INSERT INTO ai_ops_skill_maintenance(check_id,project_id,skill_id,base_version,base_skill_hash,base_package_hash,
              base_content,kind,status,reason,body_tokens,tokenizer,patch_count,automatic,last_use_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE check_id=check_id
            """,id,s.projectId(),s.skillId(),s.version(),s.skillHash(),s.packageHash(),s.content(),kind,
                "INACTIVITY_REVIEW".equals(kind)?"REVIEW_REQUIRED":"PENDING",reason,tokens,tokenizer,s.patches(),s.automatic(),
                s.lastUseAt()==null?null:Timestamp.from(s.lastUseAt()));
    }

    @Override @Transactional(transactionManager="mysqlTransactionManager")
    public Optional<Claim> claim() {
        jdbc.update("""
            UPDATE ai_ops_skill_maintenance m JOIN ai_ops_skill s
              ON s.scope='PROJECT' AND s.project_id=m.project_id AND s.skill_id=m.skill_id
            SET m.status='SUPERSEDED',m.reason='PUBLISHED_VERSION_CHANGED'
            WHERE m.status='PENDING_INDEX' AND s.current_version<>m.published_version
            """);
        jdbc.update("""
            UPDATE ai_ops_skill_maintenance m JOIN ai_ops_skill_runtime_publication p
              ON p.scope='PROJECT' AND p.project_id=m.project_id AND p.skill_id=m.skill_id AND p.skill_version=m.published_version
            SET m.status='ACTIVE',m.reason='BODY_AND_INDEX_READY' WHERE m.status='PENDING_INDEX'
            """);
        var rows=jdbc.queryForList("""
            SELECT * FROM ai_ops_skill_maintenance WHERE kind='COMPRESS_CHECK'
              AND ((status='PENDING' AND next_run_at<=CURRENT_TIMESTAMP(3))
                OR (status='RUNNING' AND lease_until<CURRENT_TIMESTAMP(3)))
            ORDER BY next_run_at,check_id LIMIT 1 FOR UPDATE SKIP LOCKED
            """);
        if(rows.isEmpty()) return Optional.empty();
        var r=rows.get(0);String id=text(r,"check_id"),token=UUID.randomUUID().toString();
        jdbc.update("UPDATE ai_ops_skill_maintenance SET status='RUNNING',attempts=attempts+1,lease_token=?,lease_until=DATE_ADD(CURRENT_TIMESTAMP(3),INTERVAL 10 MINUTE) WHERE check_id=?",token,id);
        var s=new Subject(0,text(r,"project_id"),text(r,"skill_id"),number(r,"base_version"),text(r,"base_skill_hash"),
            text(r,"base_package_hash"),text(r,"base_content"),Instant.EPOCH,null,number(r,"patch_count"),0,
            Boolean.TRUE.equals(r.get("automatic")) || r.get("automatic") instanceof Number n && n.intValue()==1);
        return Optional.of(new Claim(id,token,number(r,"attempts")+1,s));
    }

    @Override public void requireCurrent(Claim c) {
        if(jdbc.queryForList("SELECT check_id FROM ai_ops_skill_maintenance WHERE check_id=? AND status='RUNNING' AND lease_token=? AND lease_until>CURRENT_TIMESTAMP(3) FOR UPDATE",c.id(),c.token()).isEmpty())
            throw new IllegalStateException("SKILL_MAINTENANCE_LEASE_LOST");
    }
    @Override public void finish(Claim c,String status,String reason,Map<String,Object> evidence,int publishedVersion) {
        if(!Set.of("NO_CHANGE","REVIEW_REQUIRED","PENDING_INDEX","SUPERSEDED").contains(status)) throw new IllegalArgumentException("SKILL_MAINTENANCE_STATUS_INVALID");
        int count=jdbc.update("""
            UPDATE ai_ops_skill_maintenance SET status=?,reason=?,evidence_json=?,published_version=?,lease_token=NULL,lease_until=NULL
            WHERE check_id=? AND status='RUNNING' AND lease_token=? AND lease_until>CURRENT_TIMESTAMP(3)
            """,status,reason,JSON.toJSONString(evidence),publishedVersion,c.id(),c.token());
        if(count!=1) throw new IllegalStateException("SKILL_MAINTENANCE_LEASE_LOST");
    }
    @Override public void defer(Claim c,String reason) {
        int seconds=Math.min(3600,30*(1<<Math.min(7,Math.max(0,c.attempt()-1))));
        jdbc.update("""
            UPDATE ai_ops_skill_maintenance SET status='PENDING',reason=?,lease_token=NULL,lease_until=NULL,
              next_run_at=DATE_ADD(CURRENT_TIMESTAMP(3),INTERVAL ? SECOND)
            WHERE check_id=? AND status='RUNNING' AND lease_token=? AND lease_until>CURRENT_TIMESTAMP(3)
            """,reason,seconds,c.id(),c.token());
    }
    @Override public List<Map<String,Object>> list(String project,int limit) {
        return jdbc.queryForList("""
            SELECT check_id,project_id,skill_id,base_version,kind,status,reason,body_tokens,tokenizer,patch_count,
              last_use_at,attempts,next_run_at,published_version,reviewed_by,review_reason,created_at,updated_at
            FROM ai_ops_skill_maintenance WHERE (?='' OR project_id=?) ORDER BY created_at DESC,check_id LIMIT ?
            """,project,project,Math.min(100,Math.max(1,limit)));
    }
    @Override public Map<String,Object> acknowledge(String project,String id,String actor,String reason) {
        if(project==null || project.isBlank() || actor==null || actor.isBlank() || reason==null || reason.isBlank() || reason.length()>1024)
            throw new IllegalArgumentException("SKILL_MAINTENANCE_REVIEW_REASON_REQUIRED");
        int changed=jdbc.update("""
            UPDATE ai_ops_skill_maintenance SET status='REVIEWED_KEEP',reviewed_by=?,review_reason=?
            WHERE project_id=? AND check_id=? AND kind='INACTIVITY_REVIEW' AND status='REVIEW_REQUIRED'
            """,actor,reason,project,id);
        if(changed!=1) throw new IllegalStateException("SKILL_MAINTENANCE_REVIEW_CONFLICT");
        return Map.of("checkId",id,"status","REVIEWED_KEEP");
    }
    private static String text(Map<String,Object> row,String key) {return Objects.toString(row.get(key),"");}
    private static int number(Map<String,Object> row,String key) {return ((Number)row.get(key)).intValue();}
    private static Instant instant(Timestamp time) {return time==null?null:time.toInstant();}
}
