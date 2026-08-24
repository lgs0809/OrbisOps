package cn.lgs.orbisops.infrastructure.adapter.repository;
import cn.lgs.orbisops.application.skill.SkillExperienceGroupingIndexPort;
import cn.lgs.orbisops.domain.skill.model.SkillMethodMemory.*;
import cn.lgs.orbisops.domain.skill.service.SkillExperienceRecallPolicy;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import jakarta.annotation.PostConstruct;
import java.util.*;

/** Rebuildable, project-bound private experience projection, separate from runtime Skill retrieval. */
@Repository
public class PgSkillExperienceGroupingIndex implements SkillExperienceGroupingIndexPort {
    private final JdbcTemplate jdbc;
    private final SkillExperienceRecallPolicy policy=new SkillExperienceRecallPolicy();
    @Autowired public PgSkillExperienceGroupingIndex(@Qualifier("pgVectorJdbcTemplate") ObjectProvider<JdbcTemplate> provider) {this(provider.getIfAvailable());}
    public PgSkillExperienceGroupingIndex(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    @PostConstruct public void initialize() {
        if(jdbc==null) return;
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS ops_skill_experience_group_index (
              project_id varchar(128) NOT NULL,group_id varchar(64) NOT NULL,model_identity text NOT NULL,
              group_version bigint NOT NULL,content_hash varchar(64) NOT NULL,document text NOT NULL,
              terms tsvector NOT NULL,embedding vector(1024) NOT NULL,updated_at timestamptz NOT NULL DEFAULT now(),
              PRIMARY KEY(project_id,group_id,model_identity))
            """);
        jdbc.execute("ALTER TABLE ops_skill_experience_group_index ADD COLUMN IF NOT EXISTS projection_kind text NOT NULL DEFAULT 'whole-method-v1'");
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS ops_skill_experience_fact_embedding (
              project_id varchar(128) NOT NULL,source_id varchar(128) NOT NULL,source_hash varchar(128) NOT NULL,
              method_hash varchar(64) NOT NULL,model_identity text NOT NULL,embedding vector(1024) NOT NULL,
              created_at timestamptz NOT NULL DEFAULT now(),
              PRIMARY KEY(project_id,source_id,source_hash,method_hash,model_identity))
            """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_experience_group_terms ON ops_skill_experience_group_index USING gin(terms)");
    }
    @Override public Optional<float[]> factEmbedding(String project,Fact fact,String identity) {
        requireIndex();
        return jdbc.query("""
            SELECT embedding::text FROM ops_skill_experience_fact_embedding
            WHERE project_id=? AND source_id=? AND source_hash=? AND method_hash=? AND model_identity=?
            """,(r,n)->parseVector(r.getString(1)),project,fact.sourceId(),fact.sourceHash(),methodHash(fact),identity)
            .stream().findFirst();
    }
    @Override public void cacheFactEmbedding(String project,Fact fact,String identity,float[] values) {
        requireIndex();
        jdbc.update("""
            INSERT INTO ops_skill_experience_fact_embedding(project_id,source_id,source_hash,method_hash,model_identity,embedding)
            VALUES(?,?,?,?,?,?::vector) ON CONFLICT DO NOTHING
            """,project,fact.sourceId(),fact.sourceHash(),methodHash(fact),identity,vector(values));
    }
    private String methodHash(Fact fact) {return CanonicalObjectHasher.sha256(fact.method().view());}
    private float[] parseVector(String raw) {
        String[] parts=raw.substring(1,raw.length()-1).split(",");float[] result=new float[parts.length];
        for(int i=0;i<parts.length;i++) result[i]=Float.parseFloat(parts[i]);
        vector(result);return result;
    }
    @Override public boolean contains(Group g,String identity) {
        requireIndex();
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM ops_skill_experience_group_index WHERE project_id=? AND group_id=? AND group_version=? AND content_hash=? AND model_identity=? AND projection_kind='fact-centroid-v1')",
                Boolean.class,g.projectId(),g.groupId(),g.version(),g.contentHash(),identity));
    }
    @Override public void put(Group g,String identity,float[] embedding) {
        requireIndex();String document=g.document();
        jdbc.update("""
            INSERT INTO ops_skill_experience_group_index(project_id,group_id,model_identity,group_version,content_hash,document,terms,embedding,projection_kind)
            VALUES(?,?,?,?,?,?,to_tsvector('simple',?),?::vector,'fact-centroid-v1')
            ON CONFLICT(project_id,group_id,model_identity) DO UPDATE SET group_version=EXCLUDED.group_version,
              content_hash=EXCLUDED.content_hash,document=EXCLUDED.document,terms=EXCLUDED.terms,embedding=EXCLUDED.embedding,projection_kind=EXCLUDED.projection_kind,updated_at=now()
            WHERE ops_skill_experience_group_index.group_version<EXCLUDED.group_version
              OR (ops_skill_experience_group_index.group_version=EXCLUDED.group_version
                  AND ops_skill_experience_group_index.content_hash=EXCLUDED.content_hash
                  AND ops_skill_experience_group_index.projection_kind='whole-method-v1')
            """,g.projectId(),g.groupId(),identity,g.version(),g.contentHash(),document,String.join(" ",policy.terms(document)),vector(embedding));
    }
    @Override public List<Ref> search(String project,String text,String identity,float[] embedding) {
        requireIndex();if(project==null || project.isBlank()) throw new IllegalArgumentException("SKILL_GROUPING_PROJECT_REQUIRED");
        String query=String.join(" | ",policy.terms(text));
        List<Ref> lexical=query.isEmpty()?List.of():jdbc.query("""
            SELECT group_id,group_version,content_hash FROM ops_skill_experience_group_index
            WHERE project_id=? AND model_identity=? AND terms @@ to_tsquery('simple',?)
            ORDER BY ts_rank_cd(terms,to_tsquery('simple',?)) DESC,group_id LIMIT 10
            """,(r,n)->new Ref(r.getString(1),r.getLong(2),r.getString(3)),project,identity,query,query);
        var semantic=jdbc.query("""
            SELECT group_id,group_version,content_hash FROM ops_skill_experience_group_index
            WHERE project_id=? AND model_identity=? ORDER BY embedding <=> ?::vector,group_id LIMIT 10
            """,(r,n)->new Ref(r.getString(1),r.getLong(2),r.getString(3)),project,identity,vector(embedding));
        return policy.fuse(lexical,semantic);
    }
    private void requireIndex() {if(jdbc==null) throw new IllegalStateException("SKILL_GROUPING_INDEX_UNAVAILABLE");}
    private String vector(float[] values) {
        if(values==null || values.length!=1024) throw new IllegalArgumentException("SKILL_GROUPING_VECTOR_DIMENSION");
        double norm=0;var result=new StringJoiner(",","[","]");
        for(float value:values) {if(!Float.isFinite(value)) throw new IllegalArgumentException("SKILL_GROUPING_VECTOR_NON_FINITE");norm+=(double)value*value;result.add(Float.toString(value));}
        if(Math.abs(norm-1)>.01) throw new IllegalArgumentException("SKILL_GROUPING_VECTOR_NORM");return result.toString();
    }
}
