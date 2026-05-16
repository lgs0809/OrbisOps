package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.SkillRouteIndexPort;
import cn.lgs.orbisops.domain.skill.model.SkillRuntimeCandidate;
import cn.lgs.orbisops.domain.skill.service.SkillRouteProjectionIdentity;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** Immutable 1024-dimensional generations. No query can remove the live authorized version allowlist. */
@Repository
public class PgSkillRouteIndexRepository implements SkillRouteIndexPort {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    @Autowired
    public PgSkillRouteIndexRepository(@Qualifier("pgVectorJdbcTemplate") ObjectProvider<JdbcTemplate> jdbc) {
        this(jdbc.getIfAvailable());
    }
    public PgSkillRouteIndexRepository(JdbcTemplate jdbc) {
        this.jdbc=jdbc;
        this.tx=jdbc==null ? null : new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));
    }
    @PostConstruct public void initialize() {
        if(jdbc==null) return;
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS ops_skill_route_generation (
              generation_id varchar(64) PRIMARY KEY, scope varchar(16) NOT NULL, project_id varchar(128) NOT NULL,
              skill_id varchar(128) NOT NULL, skill_version integer NOT NULL, skill_hash text NOT NULL,
              package_hash text NOT NULL, model_identity text NOT NULL, dimension integer NOT NULL CHECK(dimension=1024),
              status varchar(16) NOT NULL CHECK(status IN ('BUILDING','READY')), created_at timestamptz NOT NULL DEFAULT now(), ready_at timestamptz)
            """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS ops_skill_route_document (
              generation_id varchar(64) PRIMARY KEY REFERENCES ops_skill_route_generation(generation_id),
              content_hash varchar(64) NOT NULL, routing_text text NOT NULL, embedding vector(1024) NOT NULL)
            """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_skill_route_scope ON ops_skill_route_generation(project_id,scope,status)");
    }
    @Override public void stage(SkillRuntimeCandidate c,String identity) {
        requireIndex();
        jdbc.update("""
            INSERT INTO ops_skill_route_generation(generation_id,scope,project_id,skill_id,skill_version,skill_hash,package_hash,model_identity,dimension,status)
            VALUES (?,?,?,?,?,?,?,?,1024,'BUILDING') ON CONFLICT(generation_id) DO NOTHING
            """,SkillRouteProjectionIdentity.key(c,identity),c.scope(),c.projectId(),c.skillId(),c.version(),c.skillHash(),c.packageHash(),identity);
    }
    @Override public boolean contains(SkillRuntimeCandidate c,String identity) {
        requireIndex();
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM ops_skill_route_generation WHERE generation_id=? AND status='READY')",
                Boolean.class,SkillRouteProjectionIdentity.key(c,identity)));
    }
    @Override public void ready(SkillRuntimeCandidate c,String identity,float[] embedding) {
        requireIndex();
        String vector=vector(embedding),key=SkillRouteProjectionIdentity.key(c,identity),document=SkillRouteProjectionIdentity.document(c);
        tx.executeWithoutResult(ignored->{
            var rows=jdbc.queryForList("SELECT status FROM ops_skill_route_generation WHERE generation_id=? FOR UPDATE",key);
            if(rows.size()!=1) throw new IllegalStateException("SKILL_ROUTE_GENERATION_NOT_STAGED");
            if("READY".equals(rows.get(0).get("status"))) return;
            jdbc.update("INSERT INTO ops_skill_route_document(generation_id,content_hash,routing_text,embedding) VALUES(?,?,?,?::vector)",
                    key,cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher.sha256Text(document),document,vector);
            jdbc.update("UPDATE ops_skill_route_generation SET status='READY',ready_at=now() WHERE generation_id=? AND status='BUILDING'",key);
        });
    }
    @Override public Map<String,Double> search(String project,List<SkillRuntimeCandidate> authorized,String identity,float[] embedding,int limit) {
        if(project==null || project.isBlank()) throw new IllegalArgumentException("SKILL_ROUTE_PROJECT_REQUIRED");
        if(authorized==null || authorized.isEmpty()) return Map.of();
        requireIndex();
        if(authorized.size()>20_000) throw new IllegalArgumentException("SKILL_ROUTE_ALLOWLIST_LIMIT_EXCEEDED");
        var keys=new ArrayList<String>();var ids=new HashMap<String,String>();
        for(var c:authorized) {
            if(!c.activeAtUse() || !("GLOBAL".equals(c.scope()) && c.projectId().isBlank() || "PROJECT".equals(c.scope()) && project.equals(c.projectId())))
                throw new SecurityException("SKILL_ROUTE_SCOPE_MISMATCH");
            var key=SkillRouteProjectionIdentity.key(c,identity);keys.add(key);ids.put(key,c.skillId());
        }
        // Materialize exact scores inside the authorized subset, rather than spilling
        // thousands of 1024-dimensional vectors into the CTE's intermediate storage.
        // Bind a native array so PostgreSQL can estimate the actual allowlist cardinality;
        // a JSON set-returning function is estimated at 100 rows even for 10,000 keys.
        // This bounded fallback never repairs ANN underfill with an unscoped query.
        return tx.execute(ignored->{
            jdbc.execute("SET LOCAL statement_timeout='1000ms'");
            var rows=jdbc.queryForList("""
                WITH allowed AS MATERIALIZED (
                  SELECT d.generation_id,1-(d.embedding <=> ?::vector) AS score FROM ops_skill_route_document d
                  JOIN ops_skill_route_generation g ON g.generation_id=d.generation_id
                  WHERE d.generation_id=ANY(?::varchar[]) AND g.status='READY' AND g.model_identity=? AND g.dimension=1024
                    AND (g.scope='GLOBAL' AND g.project_id='' OR g.scope='PROJECT' AND g.project_id=?))
                SELECT generation_id,score FROM allowed ORDER BY score DESC,generation_id LIMIT ?
                """,vector(embedding),new SqlArrayValue("varchar",keys.toArray()),identity,project,Math.max(1,Math.min(20,limit)));
            Map<String,Double> result=new LinkedHashMap<>();
            for(var row:rows) result.put(ids.get(String.valueOf(row.get("generation_id"))),((Number)row.get("score")).doubleValue());
            return Map.copyOf(result);
        });
    }
    private void requireIndex() {
        if(jdbc==null) throw new IllegalStateException("SKILL_ROUTE_INDEX_UNAVAILABLE");
    }
    private String vector(float[] values) {
        if(values==null || values.length!=1024) throw new IllegalArgumentException("SKILL_ROUTE_DIMENSION_MISMATCH");
        double norm=0;StringJoiner text=new StringJoiner(",","[","]");
        for(float v:values) { if(!Float.isFinite(v)) throw new IllegalArgumentException("SKILL_ROUTE_VECTOR_NON_FINITE");norm+=(double)v*v;text.add(Float.toString(v)); }
        if(Math.abs(norm-1)>0.01) throw new IllegalArgumentException("SKILL_ROUTE_VECTOR_NOT_NORMALIZED");return text.toString();
    }
}
