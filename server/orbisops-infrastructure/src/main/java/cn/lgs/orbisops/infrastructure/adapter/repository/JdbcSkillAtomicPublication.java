package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.*;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillPackageRepository;
import cn.lgs.orbisops.domain.skill.model.*;
import cn.lgs.orbisops.domain.skill.service.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
import java.util.function.BiConsumer;

/** Shares the route repository's MySQL transaction; PG generations are immutable prerequisites. */
final class JdbcSkillAtomicPublication {
    private final JdbcTemplate jdbc;
    private final JdbcSkillCatalogRepository catalog;
    private final ISkillPackageRepository packages;
    private final SkillRouteIndexPort index;
    private final java.util.function.Function<String,SkillAtomicPublicationPlan> currentPlan;
    JdbcSkillAtomicPublication(JdbcTemplate jdbc, JdbcSkillCatalogRepository catalog,
            ISkillPackageRepository packages, SkillRouteIndexPort index,java.util.function.Function<String,SkillAtomicPublicationPlan> currentPlan) {
        this.jdbc=jdbc; this.catalog=catalog; this.packages=packages; this.index=index;
        this.currentPlan=currentPlan;
    }

    Optional<String> targetGroup(SkillRuntimeCandidate c) {
        if (!"PROJECT".equals(c.scope())) return Optional.empty();
        var rows=jdbc.queryForList("SELECT candidate_id FROM ai_ops_skill_atomic_member WHERE project_id=? AND skill_id=? AND role='TARGET'",
                c.projectId(),c.skillId());
        if (rows.size()>1) throw new IllegalStateException("SKILL_ATOMIC_TARGET_IDENTITY_CONFLICT");
        return rows.stream().findFirst().map(r->String.valueOf(r.get("candidate_id")));
    }

    void stage(String candidateId, SkillAtomicPublicationPlan plan, List<SkillPublicationOutcome> outcomes) {
        requireTransaction();
        if(outcomes.size()!=plan.targets().size() || outcomes.stream().anyMatch(o->!o.published())) fail("PACKAGE_SET_INCOMPLETE");
        String json=CanonicalJson.stringify(plan);
        if(!json.equals(CanonicalJson.stringify(currentPlan.apply(candidateId)))) fail("PLAN_CONFLICT");
        var existing=jdbc.queryForList("SELECT plan_json,plan_hash FROM ai_ops_skill_atomic_publication WHERE candidate_id=? FOR UPDATE",candidateId);
        if(!existing.isEmpty()) {
            if(!json.equals(existing.get(0).get("plan_json")) || !CanonicalObjectHasher.sha256Text(json).equals(existing.get(0).get("plan_hash"))) fail("PLAN_CONFLICT");
            return;
        }
        requireSources(plan.projectId(),plan.sources(),plan.operation());
        jdbc.update("INSERT INTO ai_ops_skill_atomic_publication(candidate_id,project_id,operation,status,plan_json,plan_hash) VALUES(?,?,?,'STAGED',?,?)",
                candidateId,plan.projectId(),plan.operation(),json,CanonicalObjectHasher.sha256Text(json));
        for(var s:plan.sources()) member(candidateId,plan.projectId(),"SOURCE",s.skillId(),s.version(),s.skillHash(),s.packageHash());
        for(var target:plan.targets()) {
            var matching=outcomes.stream().filter(o->o.skillId().equals(target.skillId())).toList();
            if(matching.size()!=1) fail("PACKAGE_SET_INCOMPLETE");
            var o=matching.get(0);
            var entry=catalog.lockEvolutionEntry("PROJECT",plan.projectId(),target.skillId()).orElseThrow();
            if(o.version()!=entry.currentVersion() || !o.skillHash().equals(entry.currentSkillHash())) fail("TARGET_CHANGED");
            member(candidateId,plan.projectId(),"TARGET",o.skillId(),o.version(),o.skillHash(),entry.currentPackageHash());
        }
    }

    boolean activate(String candidateId, String identity, BiConsumer<SkillRuntimeCandidate,String> writePointer) {
        requireTransaction();
        var group=lock(candidateId); String status=String.valueOf(group.get("status"));
        if("ROLLED_BACK".equals(status)) return false;
        var members=members(candidateId);
        // A projection repair after activation may rebuild a model generation, but cannot change membership.
        var targets=targets(group,members);
        if(targets.stream().anyMatch(c->!index.contains(c,identity))) return false;
        for(var c:targets) readyBody(c);
        if("STAGED".equals(status)) {
            var plan=currentPlan.apply(candidateId);
            if(!CanonicalJson.stringify(plan).equals(group.get("plan_json"))) fail("PLAN_CONFLICT");
            requireSources(plan.projectId(),plan.sources(),plan.operation());
            for(var source:plan.sources()) jdbc.update("INSERT INTO ai_ops_skill_atomic_replacement(project_id,source_skill_id,candidate_id) VALUES(?,?,?)",
                    plan.projectId(),source.skillId(),candidateId);
        }
        for(var c:targets) writePointer.accept(c,identity);
        jdbc.update("UPDATE ai_ops_skill_atomic_publication SET status='ACTIVE' WHERE candidate_id=?",candidateId);
        return true;
    }

    boolean active(String candidateId) {
        return jdbc.queryForList("SELECT candidate_id FROM ai_ops_skill_atomic_publication WHERE candidate_id=? AND status='ACTIVE'",candidateId).size()==1;
    }

    void rollback(String project, String candidateId, String actor, String reason) {
        requireTransaction();
        if(actor==null || actor.isBlank() || actor.length()>128 || reason==null || reason.isBlank() || reason.length()>1024) fail("ROLLBACK_REASON_REQUIRED");
        var group=lock(candidateId);
        if(!project.equals(group.get("project_id"))) throw new SecurityException("SKILL_ATOMIC_PROJECT_MISMATCH");
        if("ROLLED_BACK".equals(group.get("status"))) return;
        var members=members(candidateId);
        targets(group,members); // CAS: later edits cannot be silently undone by a group rollback.
        for(var m:members) if("SOURCE".equals(m.get("role"))) {
            var entry=catalog.lockEvolutionEntry("PROJECT",project,String.valueOf(m.get("skill_id"))).orElseThrow();
            requireMember(entry,m);
        }
        for(var m:members) if("TARGET".equals(m.get("role"))) {
            // A later split/merge depending on this target must be rolled back first.
            if(!jdbc.queryForList("SELECT candidate_id FROM ai_ops_skill_atomic_replacement WHERE project_id=? AND source_skill_id=?",
                    project,m.get("skill_id")).isEmpty()) fail("DEPENDENT_PUBLICATION_ACTIVE");
        }
        jdbc.update("DELETE FROM ai_ops_skill_atomic_replacement WHERE project_id=? AND candidate_id=?",project,candidateId);
        jdbc.update("UPDATE ai_ops_skill_atomic_publication SET status='ROLLED_BACK',rollback_actor=?,rollback_reason=? WHERE candidate_id=?",actor,reason,candidateId);
        // Retain pointers as historical evidence; visibility and projection activation reject this entire group.
    }

    List<SkillRuntimeCandidate> filter(String project,List<SkillRuntimeCandidate> current,Set<String> explicit) {
        return new JdbcSkillLifecycleVisibility(jdbc).filter(project,current,SkillRuntimeCandidate::scope,SkillRuntimeCandidate::skillId,explicit);
    }

    private void requireSources(String project,List<SkillAtomicPublicationPlan.Source> sources,String operation) {
        for(var s:sources.stream().sorted(Comparator.comparing(SkillAtomicPublicationPlan.Source::skillId)).toList()) {
            var entry=catalog.lockEvolutionEntry("PROJECT",project,s.skillId()).orElseThrow();
            if(!entry.autoPublishSkipReason().isBlank() || !SkillEvolutionRelatedSkillPolicy.databaseFence(entry).equals(s.catalogFence())) fail("SOURCE_CHANGED_OR_PROTECTED");
            if("MERGE_SKILLS".equals(operation) && !entry.autoMergeEnabled()) fail("SOURCE_MERGE_DISABLED");
            if(!jdbc.queryForList("SELECT candidate_id FROM ai_ops_skill_atomic_replacement WHERE project_id=? AND source_skill_id=?",project,s.skillId()).isEmpty()) fail("SOURCE_ALREADY_REPLACED");
            var candidate=SkillCatalogSnapshot.fromView(new SkillCatalogViewMapper().toView(entry,false)).runtimeCandidate();
            if(filter(project,List.of(candidate),Set.of()).isEmpty()) fail("SOURCE_NOT_ACTIVE");
        }
    }
    private Map<String,Object> lock(String candidate) {
        var rows=jdbc.queryForList("SELECT * FROM ai_ops_skill_atomic_publication WHERE candidate_id=? FOR UPDATE",candidate);
        if(rows.size()!=1) fail("PUBLICATION_NOT_FOUND");
        var row=rows.get(0);
        if(!CanonicalObjectHasher.sha256Text(String.valueOf(row.get("plan_json"))).equals(row.get("plan_hash"))) fail("PLAN_CORRUPT");
        return row;
    }
    private List<Map<String,Object>> members(String id) {
        return jdbc.queryForList("SELECT * FROM ai_ops_skill_atomic_member WHERE candidate_id=? ORDER BY skill_id,role",id);
    }
    private List<SkillRuntimeCandidate> targets(Map<String,Object> group,List<Map<String,Object>> members) {
        var result=new ArrayList<SkillRuntimeCandidate>();
        for(var m:members) if("TARGET".equals(m.get("role"))) {
            var entry=catalog.lockEvolutionEntry("PROJECT",String.valueOf(group.get("project_id")),String.valueOf(m.get("skill_id"))).orElseThrow();
            requireMember(entry,m);
            if(!entry.governanceState().activeAtUse()) fail("TARGET_DISABLED");
            result.add(SkillCatalogSnapshot.fromView(new SkillCatalogViewMapper().toView(entry,false)).runtimeCandidate());
        }
        var plan=CanonicalJson.parseObject(String.valueOf(group.get("plan_json")));
        if(result.isEmpty() || !(plan.get("targets") instanceof List<?> expected) || result.size()!=expected.size()) fail("PACKAGE_SET_INCOMPLETE");
        return result;
    }
    private void requireMember(SkillCatalogEntry entry,Map<String,Object> m) {
        if(entry.currentVersion()!=((Number)m.get("skill_version")).intValue() || !entry.currentSkillHash().equals(m.get("skill_hash"))
                || !entry.currentPackageHash().equals(m.get("package_hash"))) fail("VERSION_CHANGED");
    }
    private void readyBody(SkillRuntimeCandidate c) {
        var key=new SkillPackageKey(c.scope(),c.projectId(),c.skillId(),c.version());
        new SkillPackageReadinessPolicy().requireReady(c,packages.findVersion(key).orElse(null),packages.findArtifacts(key));
    }
    private void member(String candidate,String project,String role,String id,int version,String hash,String packageHash) {
        jdbc.update("INSERT INTO ai_ops_skill_atomic_member(candidate_id,project_id,role,skill_id,skill_version,skill_hash,package_hash) VALUES(?,?,?,?,?,?,?)",
                candidate,project,role,id,version,hash,packageHash);
    }
    private void requireTransaction() {
        if(!TransactionSynchronizationManager.isActualTransactionActive()) fail("TRANSACTION_REQUIRED");
    }
    private static void fail(String reason) {throw new IllegalStateException("SKILL_ATOMIC_"+reason);}
}
