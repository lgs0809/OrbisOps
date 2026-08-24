package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.*;
import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillPackageRepository;
import cn.lgs.orbisops.domain.skill.model.*;
import cn.lgs.orbisops.domain.skill.service.*;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** MySQL pointer is committed after immutable PG READY and body checks; no distributed transaction. */
@Repository
public class JdbcSkillRoutePublicationRepository implements SkillRoutePublicationPort, SkillAtomicPublicationPort {
    private final JdbcTemplate jdbc;
    private final JdbcSkillCatalogRepository catalog;
    private final ISkillPackageRepository packages;
    private final SkillRouteIndexPort index;
    private final TransactionTemplate tx;
    private final JdbcSkillAtomicPublication atomic;
    @Autowired
    public JdbcSkillRoutePublicationRepository(@Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbc,
            JdbcSkillCatalogRepository catalog, ISkillPackageRepository packages, SkillRouteIndexPort index) {
        this(jdbc.getIfAvailable(), catalog, packages, index);
    }
    public JdbcSkillRoutePublicationRepository(JdbcTemplate jdbc, JdbcSkillCatalogRepository catalog,
            ISkillPackageRepository packages, SkillRouteIndexPort index) {
        this(jdbc,catalog,packages,index,id -> {
            var candidate=new JdbcSkillPatchCandidateAdapter(jdbc).get(id);
            return SkillAtomicPublicationPlan.from(candidate,new JdbcSkillEvolutionProposalAdapter(jdbc).publicationInput(candidate));
        });
    }
    JdbcSkillRoutePublicationRepository(JdbcTemplate jdbc,JdbcSkillCatalogRepository catalog,ISkillPackageRepository packages,
            SkillRouteIndexPort index,java.util.function.Function<String,SkillAtomicPublicationPlan> currentPlan) {
        this.jdbc = jdbc; this.catalog = catalog; this.packages = packages; this.index = index;
        tx = jdbc == null ? null : new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));
        atomic=new JdbcSkillAtomicPublication(jdbc,catalog,packages,index,currentPlan);
    }
    @Override public boolean activate(SkillRuntimeCandidate c, String identity) {
        requireAvailable(identity);
        if (!index.contains(c, identity)) return false;
        return Boolean.TRUE.equals(tx.execute(ignored -> {
            var group=atomic.targetGroup(c);
            if(group.isPresent() && !atomic.active(group.get())) return atomic.activate(group.get(),identity,this::writePointer);
            var head = catalog.lockEvolutionEntry(c.scope(), c.projectId(), c.skillId()).orElse(null);
            if (head == null || !head.governanceState().activeAtUse() || head.currentVersion() != c.version()
                    || !head.currentSkillHash().equals(c.skillHash()) || !head.currentPackageHash().equals(c.packageHash())) return false;
            var key = new SkillPackageKey(c.scope(), c.projectId(), c.skillId(), c.version());
            var version = packages.findVersion(key).orElse(null);
            new SkillPackageReadinessPolicy().requireReady(c, version, packages.findArtifacts(key));
            // A delayed index build must not activate a version whose successful source was corrected meanwhile.
            if(version != null && !version.evolutionJobId().isBlank()) {
                var releases=jdbc.queryForList("""
                        SELECT candidate_id FROM ai_ops_skill_release WHERE candidate_id=?
                          AND JSON_UNQUOTE(JSON_EXTRACT(metadata_json,'$.publicationPolicy'))='source-qualified-auto-v1'
                        """,version.evolutionJobId());
                if(!releases.isEmpty()) new JdbcSkillEvolutionProposalAdapter(jdbc).publicationInput(
                        new JdbcSkillPatchCandidateAdapter(jdbc).get(version.evolutionJobId()),false);
            }
            writePointer(c,identity);
            return true;
        }));
    }
    private void writePointer(SkillRuntimeCandidate c,String identity) {
            String snapshot = CanonicalJson.stringify(c);
            jdbc.update("""
                INSERT INTO ai_ops_skill_runtime_publication
                  (scope,project_id,skill_id,model_identity_hash,model_identity,skill_version,skill_hash,package_hash,generation_id,metadata_json,metadata_hash)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE skill_version=VALUES(skill_version),skill_hash=VALUES(skill_hash),package_hash=VALUES(package_hash),
                  generation_id=VALUES(generation_id),metadata_json=VALUES(metadata_json),metadata_hash=VALUES(metadata_hash),published_at=CURRENT_TIMESTAMP(3)
                """, c.scope(), c.projectId(), c.skillId(), CanonicalObjectHasher.sha256Text(identity), identity,
                    c.version(), c.skillHash(), c.packageHash(), SkillRouteProjectionIdentity.key(c, identity), snapshot, CanonicalObjectHasher.sha256Text(snapshot));
    }
    @Override public List<SkillRuntimeCandidate> visible(String project, List<SkillRuntimeCandidate> current, String identity) {
        return visible(project,current,identity,Set.of());
    }
    @Override public List<SkillRuntimeCandidate> visible(String project,List<SkillRuntimeCandidate> current,String identity,Set<String> explicit) {
        requireAvailable(identity);
        return tx.execute(ignored -> atomic.filter(project,visibleSnapshot(project,current,identity),explicit));
    }
    @Override public List<SkillRuntimeCandidate> lifecycleVisible(String project,List<SkillRuntimeCandidate> current,Set<String> explicit) {
        if(jdbc==null) throw new IllegalStateException("SKILL_PUBLICATION_STORE_UNAVAILABLE");
        return tx.execute(ignored -> atomic.filter(project,current,explicit));
    }
    private List<SkillRuntimeCandidate> visibleSnapshot(String project, List<SkillRuntimeCandidate> current, String identity) {
        requireAvailable(identity);
        if (project == null || project.isBlank() || current.size() > 20_000) throw new IllegalArgumentException("SKILL_PUBLICATION_SCOPE_REQUIRED");
        if (current.isEmpty()) return List.of();
        Map<String, SkillRuntimeCandidate> allowed = new HashMap<>();
        for (var c : current) {
            if (!c.activeAtUse() || !("GLOBAL".equals(c.scope()) && c.projectId().isBlank()
                    || "PROJECT".equals(c.scope()) && project.equals(c.projectId()))) throw new SecurityException("SKILL_PUBLICATION_SCOPE_MISMATCH");
            if (allowed.put(c.skillId(), c) != null) throw new IllegalArgumentException("SKILL_PUBLICATION_DUPLICATE_ID");
        }
        var rows = jdbc.queryForList("""
            SELECT p.* FROM ai_ops_skill_runtime_publication p
            JOIN JSON_TABLE(?, '$[*]' COLUMNS(skill_id VARCHAR(128) PATH '$')) a ON a.skill_id=p.skill_id
            WHERE p.model_identity_hash=? AND p.model_identity=?
              AND (p.scope='GLOBAL' AND p.project_id='' OR p.scope='PROJECT' AND p.project_id=?)
            """, CanonicalJson.stringify(allowed.keySet()), CanonicalObjectHasher.sha256Text(identity), identity, project);
        List<SkillRuntimeCandidate> result = new ArrayList<>();
        for (var row : rows) {
            var head = allowed.get(String.valueOf(row.get("skill_id")));
            if (!head.scope().equals(row.get("scope")) || !head.projectId().equals(row.get("project_id"))) continue;
            String raw = String.valueOf(row.get("metadata_json"));
            if (!CanonicalObjectHasher.sha256Text(raw).equals(row.get("metadata_hash"))) throw new IllegalStateException("SKILL_PUBLICATION_METADATA_CORRUPT");
            var c = SkillPublishedCandidateCodec.decode(raw, head.governanceState());
            if (!c.skillId().equals(head.skillId()) || !c.scope().equals(head.scope()) || !c.projectId().equals(head.projectId())
                    || !SkillRouteProjectionIdentity.key(c, identity).equals(row.get("generation_id"))) throw new IllegalStateException("SKILL_PUBLICATION_IDENTITY_CORRUPT");
            if (c.version() > head.version() || c.version() == head.version()
                    && (!c.skillHash().equals(head.skillHash()) || !c.packageHash().equals(head.packageHash()))) continue;
            result.add(c);
        }
        return List.copyOf(result);
    }
    @Override public void stage(String candidateId,SkillAtomicPublicationPlan plan,List<SkillPublicationOutcome> outcomes) {
        atomic.stage(candidateId,plan,outcomes);
    }
    @Override public boolean active(String candidateId) {return atomic.active(candidateId);}
    @Override public void rollback(String projectId,String candidateId,String actor,String reason) {
        tx.executeWithoutResult(ignored -> atomic.rollback(projectId,candidateId,actor,reason));
    }
    @Override public List<Map<String,Object>> list(String project,int limit) {
        return jdbc.queryForList("""
                SELECT g.*,r.status AS release_status FROM ai_ops_skill_atomic_publication g
                LEFT JOIN ai_ops_skill_release r ON r.candidate_id=g.candidate_id
                WHERE (?='' OR g.project_id=?) ORDER BY g.update_time DESC,g.candidate_id LIMIT ?
                """,project==null?"":project,project==null?"":project,Math.max(1,Math.min(100,limit))).stream().map(row -> {
                    var result=new LinkedHashMap<String,Object>();
                    result.put("candidateId",row.get("candidate_id"));result.put("projectId",row.get("project_id"));
                    result.put("operation",row.get("operation"));result.put("status",row.get("status"));
                    result.put("releaseStatus",row.get("release_status"));
                    result.put("plan",CanonicalJson.parseObject(String.valueOf(row.get("plan_json"))));
                    result.put("rollbackReason",row.get("rollback_reason"));return (Map<String,Object>)result;
                }).toList();
    }
    private void requireAvailable(String identity) {
        if (identity == null || identity.isBlank()) throw new IllegalArgumentException("SKILL_PUBLICATION_MODEL_REQUIRED");
        if (jdbc == null) throw new IllegalStateException("SKILL_PUBLICATION_STORE_UNAVAILABLE");
    }
}
