package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.*;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.skill.model.*;
import cn.lgs.orbisops.domain.skill.service.SkillEvolutionSourceSetPolicy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.lgs.orbisops.infrastructure.adapter.repository.TaskEpisodeJson.*;

/** Freezes complete author input and output; no network or model call occurs inside these transactions. */
@Repository
public class JdbcSkillEvolutionProposalAdapter implements SkillEvolutionProposalPort {
    private final JdbcTemplate jdbc;
    public JdbcSkillEvolutionProposalAdapter(@Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Override @Transactional(transactionManager="mysqlTransactionManager",isolation=Isolation.READ_COMMITTED)
    public Optional<SkillEvolutionProposalSnapshot> existing(SkillEvolutionJobSnapshot claim,String sourceHash) {
        requireClaimIdentity(claim);
        var rows=jdbc.queryForList("SELECT plan_id,plan_hash FROM ai_ops_skill_evolution_proposal WHERE job_id=? AND source_id=?",claim.jobId(),claim.sourceId());
        return rows.isEmpty()?Optional.empty():Optional.of(current(claim,text(rows.get(0).get("plan_id")),text(rows.get(0).get("plan_hash")),sourceHash));
    }

    @Override @Transactional(transactionManager="mysqlTransactionManager",isolation=Isolation.READ_COMMITTED)
    public SkillEvolutionProposalSnapshot freeze(SkillEvolutionJobSnapshot claim,String sourceHash,String clusterKey,Map<String,Object> input) {
        requireClaimIdentity(claim);
        var existing=existing(claim,sourceHash);
        if(existing.isPresent()) return existing.orElseThrow();
        String id="skill-plan-"+UUID.randomUUID();
        var frozen=new LinkedHashMap<>(input);
        frozen.put("evolutionClusterKey",clusterKey);
        frozen.put("evolutionSourceHash",sourceHash);
        frozen.put("proposalPolicyVersion","accepted-source-proposal-v1");
        frozen.put("relatedSkillPolicyVersion",cn.lgs.orbisops.domain.skill.service.SkillEvolutionRelatedSkillPolicy.VERSION);
        frozen=new LinkedHashMap<>(new JdbcSkillEvolutionSourcePortfolio(jdbc).enrich(claim.projectId(),frozen));
        String raw=CanonicalJson.stringify(frozen);
        // This is the immutable archive; model input is independently bounded by its directory reader.
        if(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>16_000_000)
            throw new IllegalStateException("SKILL_AUTHORING_SOURCE_TOO_LARGE");
        lockAndVerify(claim,sourceHash,frozen);
        // Another attempt cannot replace an input after it has been exposed to the author.
        jdbc.update("""
                INSERT IGNORE INTO ai_ops_skill_evolution_proposal
                  (plan_id,job_id,source_id,project_id,agent_id,cluster_key,plan_hash,input_json)
                VALUES (?,?,?,?,?,?,?,?)
                """,id,claim.jobId(),claim.sourceId(),claim.projectId(),claim.agentId(),clusterKey,hash(raw),raw);
        return existing(claim,sourceHash).orElseThrow(()->new IllegalStateException("SKILL_EVOLUTION_PROPOSAL_REQUIRED"));
    }

    @Override @Transactional(transactionManager="mysqlTransactionManager",isolation=Isolation.READ_COMMITTED)
    public boolean supersedeChangedBaseline(SkillEvolutionJobSnapshot claim,String sourceHash) {
        requireClaimIdentity(claim);
        var rows=jdbc.queryForList("SELECT plan_id,plan_hash FROM ai_ops_skill_evolution_proposal WHERE job_id=? AND source_id=?",claim.jobId(),claim.sourceId());
        if(rows.isEmpty()) return false;
        String id=text(rows.get(0).get("plan_id"));
        var plan=read(claim,id,text(rows.get(0).get("plan_hash")));
        // Same source/episode/job lock order as candidate publication. Revoked evidence cannot be refreshed.
        lockAndVerify(claim,sourceHash,plan.input(),true,false);
        var locked=jdbc.queryForList("SELECT candidate_id FROM ai_ops_skill_evolution_proposal WHERE plan_id=? FOR UPDATE",id);
        if(locked.size()!=1 || !text(locked.get(0).get("candidate_id")).isBlank()) return false;
        try {
            new JdbcSkillEvolutionRelatedSkillGuard(jdbc).lockAndVerify(claim.projectId(),plan.input());
            return false;
        } catch(IllegalStateException changed) {
            if(!"SKILL_EVOLUTION_RELATED_SKILL_CHANGED".equals(changed.getMessage())) throw changed;
        }
        jdbc.update("""
                INSERT INTO ai_ops_skill_evolution_proposal_history
                  (plan_id,job_id,source_id,project_id,agent_id,cluster_key,plan_hash,input_json,authored_json,
                   authored_hash,create_time,superseded_reason,superseded_epoch)
                SELECT plan_id,job_id,source_id,project_id,agent_id,cluster_key,plan_hash,input_json,authored_json,
                   authored_hash,create_time,'SKILL_EVOLUTION_RELATED_SKILL_CHANGED',?
                FROM ai_ops_skill_evolution_proposal WHERE plan_id=? AND candidate_id=''
                """,claim.epoch(),id);
        // Archive and remove from the active job slot atomically; the immutable bytes remain queryable above.
        if(jdbc.update("DELETE FROM ai_ops_skill_evolution_proposal WHERE plan_id=? AND candidate_id=''",id)!=1)
            throw new IllegalStateException("SKILL_EVOLUTION_PROPOSAL_SUPERSEDE_CONFLICT");
        return true;
    }

    @Override @Transactional(transactionManager="mysqlTransactionManager",isolation=Isolation.READ_COMMITTED)
    public SkillEvolutionProposalSnapshot authored(SkillEvolutionJobSnapshot claim,SkillEvolutionProposalSnapshot plan,Map<String,Object> result) {
        current(claim,plan.planId(),plan.planHash(),text(plan.input().get("evolutionSourceHash")));
        new JdbcSkillSourceBatchReview(jdbc).requireComplete(plan.planId(),plan.input(),result);
        if(result==null || result.isEmpty()) throw new IllegalStateException("SKILL_AUTHORING_RESULT_REQUIRED");
        String raw=CanonicalJson.stringify(result);
        if(raw.length()>1_000_000) throw new IllegalStateException("SKILL_AUTHORING_RESULT_TOO_LARGE");
        jdbc.update("UPDATE ai_ops_skill_evolution_proposal SET authored_json=?,authored_hash=? WHERE plan_id=? AND authored_json IS NULL",raw,hash(raw),plan.planId());
        return read(claim,plan.planId(),plan.planHash());
    }

    @Override @Transactional(transactionManager="mysqlTransactionManager",isolation=Isolation.READ_COMMITTED)
    public Optional<Map<String,Object>> batchReview(SkillEvolutionJobSnapshot claim,SkillEvolutionProposalSnapshot plan,int index,String digest) {
        var current=current(claim,plan.planId(),plan.planHash(),text(plan.input().get("evolutionSourceHash")));
        return new JdbcSkillSourceBatchReview(jdbc).read(current.planId(),current.input(),index,digest);
    }
    @Override @Transactional(transactionManager="mysqlTransactionManager",isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> saveBatchReview(SkillEvolutionJobSnapshot claim,SkillEvolutionProposalSnapshot plan,int index,String digest,Map<String,Object> review) {
        var current=current(claim,plan.planId(),plan.planHash(),text(plan.input().get("evolutionSourceHash")));
        return new JdbcSkillSourceBatchReview(jdbc).save(current.planId(),current.input(),index,digest,review);
    }

    /** Called in candidate's transaction; locks every source before checking the live job and plan. */
    SkillEvolutionProposalSnapshot current(SkillEvolutionJobSnapshot claim,String id,String expectedHash,String sourceHash) {
        requireClaimIdentity(claim);
        var plan=read(claim,id,expectedHash);
        lockAndVerify(claim,sourceHash,plan.input());
        if(!plan.authored().isEmpty()) new JdbcSkillSourceBatchReview(jdbc).requireComplete(plan.planId(),plan.input(),plan.authored());
        jdbc.queryForList("SELECT plan_id FROM ai_ops_skill_evolution_proposal WHERE plan_id=? FOR UPDATE",id);
        return read(claim,id,expectedHash);
    }

    /** Reserve the exact affected asset set and retain one pending proposal for at least 24 hours. */
    void reserveAssets(SkillEvolutionProposalSnapshot plan,SkillPatchCandidate candidate) {
        var input=plan.input();
        // Ordinary CREATE/PATCH asset identity. Multi-asset operations require the later atomic package gate.
        List<String> assets=candidate.targetSkillId().isBlank()
                ?List.of("new-method:"+text(input.get("evolutionClusterKey")))
                :List.of("skill:"+candidate.targetSkillId());
        String assetsJson=CanonicalJson.stringify(assets);
        String key=hash(candidate.projectId()+":"+assetsJson);
        long now=jdbc.queryForObject("SELECT CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AS UNSIGNED)",Long.class);
        jdbc.update("""
                INSERT IGNORE INTO ai_ops_skill_pending_asset_proposal
                  (asset_set_hash,project_id,assets_json,plan_id,candidate_id,next_proposal_at_ms)
                VALUES (?,?,?,?,?,?)
                """,key,candidate.projectId(),assetsJson,plan.planId(),candidate.candidateId(),now+86_400_000L);
        var row=jdbc.queryForMap("SELECT * FROM ai_ops_skill_pending_asset_proposal WHERE asset_set_hash=? FOR UPDATE",key);
        if(!candidate.projectId().equals(row.get("project_id")) || !assetsJson.equals(row.get("assets_json")))
            throw new IllegalStateException("SKILL_EVOLUTION_ASSET_IDENTITY_CONFLICT");
        if(!plan.planId().equals(row.get("plan_id"))) {
            var prior=jdbc.queryForList("SELECT status FROM ai_ops_skill_patch_candidate WHERE candidate_id=?",row.get("candidate_id"));
            boolean resolved=prior.size()==1 && Set.of("ACTIVE","ROLLED_BACK","REJECTED","DISABLED","POLICY_REJECTED","VALIDATION_FAILED")
                    .contains(text(prior.get(0).get("status")));
            if(number(row.get("next_proposal_at_ms"))>now || !resolved)
                throw new IllegalStateException("SKILL_EVOLUTION_PROPOSAL_PENDING");
            jdbc.update("UPDATE ai_ops_skill_pending_asset_proposal SET plan_id=?,candidate_id=?,next_proposal_at_ms=? WHERE asset_set_hash=?",
                    plan.planId(),candidate.candidateId(),now+86_400_000L,key);
        }
        jdbc.update("UPDATE ai_ops_skill_evolution_proposal SET candidate_id=?,asset_set_hash=? WHERE plan_id=?",candidate.candidateId(),key,plan.planId());
    }

    private SkillEvolutionProposalSnapshot read(SkillEvolutionJobSnapshot claim,String id,String expectedHash) {
        var rows=jdbc.queryForList("SELECT * FROM ai_ops_skill_evolution_proposal WHERE plan_id=? AND job_id=? AND source_id=? AND project_id=? AND agent_id=?",
                id,claim.jobId(),claim.sourceId(),claim.projectId(),claim.agentId());
        if(rows.size()!=1) throw new IllegalStateException("SKILL_EVOLUTION_PROPOSAL_REQUIRED");
        var row=rows.get(0);String raw=text(row.get("input_json")),authored=text(row.get("authored_json"));
        if(!hash(raw).equals(expectedHash) || !expectedHash.equals(row.get("plan_hash"))
                || !CanonicalJson.stringify(object(raw)).equals(raw)
                || (!authored.isBlank() && !hash(authored).equals(row.get("authored_hash"))))
            throw new IllegalStateException("SKILL_EVOLUTION_PROPOSAL_HASH_MISMATCH");
        return new SkillEvolutionProposalSnapshot(id,expectedHash,raw,authored);
    }

    private void lockAndVerify(SkillEvolutionJobSnapshot claim,String sourceHash,Map<String,Object> input) {
        lockAndVerify(claim,sourceHash,input,true);
    }

    /** A release outlives its job lease, but never the accepted source revisions that justify it. */
    Map<String,Object> publicationInput(SkillPatchCandidate candidate) {
        return publicationInput(candidate,true);
    }

    Map<String,Object> publicationInput(SkillPatchCandidate candidate,boolean unpublished) {
        var rows=jdbc.queryForList("SELECT * FROM ai_ops_skill_evolution_proposal WHERE candidate_id=? AND project_id=? AND agent_id=?",
                candidate.candidateId(),candidate.projectId(),candidate.agentId());
        if(rows.size()!=1) throw new IllegalStateException("SKILL_EVOLUTION_PROPOSAL_REQUIRED");
        var row=rows.get(0);
        String raw=text(row.get("input_json")), authored=text(row.get("authored_json"));
        if(!hash(raw).equals(row.get("plan_hash")) || !hash(authored).equals(row.get("authored_hash"))
                || !CanonicalJson.stringify(object(raw)).equals(raw))
            throw new IllegalStateException("SKILL_EVOLUTION_PROPOSAL_HASH_MISMATCH");
        var plan=new SkillEvolutionProposalSnapshot(text(row.get("plan_id")),text(row.get("plan_hash")),raw,authored);
        new JdbcSkillSourceBatchReview(jdbc).requireComplete(plan.planId(),plan.input(),plan.authored());
        var source=jdbc.queryForMap("SELECT session_id,run_id FROM ai_ops_skill_evolution_source WHERE source_id=? AND project_id=?",
                row.get("source_id"),candidate.projectId());
        if(!candidate.sourceRunId().equals(source.get("run_id"))) throw new IllegalStateException("SKILL_EVOLUTION_CANDIDATE_SCOPE_MISMATCH");
        var claim=new SkillEvolutionJobSnapshot(0,text(row.get("job_id")),candidate.sourceRunId(),text(source.get("session_id")),
                candidate.projectId(),candidate.agentId(),"",SkillEvolutionJobStatus.PENDING,0,null,"",null,null);
        lockAndVerify(claim,text(plan.input().get("evolutionSourceHash")),plan.input(),false,unpublished);
        new JdbcSkillPatchCandidateAdapter(jdbc).requireAuthoredPayload(candidate,plan.authored());
        if(unpublished) {
            new JdbcSkillEvolutionRelatedSkillGuard(jdbc).requireTarget(plan,candidate);
            new JdbcSkillNovelSourceGuard(jdbc).requireNovel(plan,candidate);
        }
        return plan.input();
    }

    private void lockAndVerify(SkillEvolutionJobSnapshot claim,String sourceHash,Map<String,Object> input,boolean liveClaim) {
        lockAndVerify(claim,sourceHash,input,liveClaim,true);
    }

    private void lockAndVerify(SkillEvolutionJobSnapshot claim,String sourceHash,Map<String,Object> input,boolean liveClaim,boolean baseline) {
        if(!sourceHash.equals(input.get("evolutionSourceHash"))) throw new IllegalStateException("SKILL_EVOLUTION_PROPOSAL_SOURCE_CHANGED");
        var samples=samples(input);
        new SkillEvolutionSourceSetPolicy().requireUsable(samples,claim.projectId(),claim.runId(),sourceHash);
        // Share the existing correlation scope lock so a concurrent regroup cannot inflate source counts at commit.
        var environments=new TreeSet<String>();
        for(var s:samples) for(var receipt:maps(object(s.episodeJson()).get("receipts"))) {
            var normalized=object(object(receipt.get("fullOutput")).get("normalizedContent"));
            String environment=text(object(normalized.get("scope")).get("environment"));
            if(!environment.isBlank()) environments.add(environment);
        }
        for(String environment:environments) jdbc.update("INSERT INTO ai_ops_alert_correlation_scope(scope_key,project_id,environment) VALUES (?,?,?) ON DUPLICATE KEY UPDATE scope_key=VALUES(scope_key)",
                JdbcAlertCorrelationStore.scope(claim.projectId(),environment),claim.projectId(),environment);
        for(String session:samples.stream().map(SkillExperienceConsolidationSample::sessionId).distinct().sorted().toList())
            jdbc.queryForList("SELECT session_id FROM ai_ops_task_episode_session WHERE session_id=? AND project_id=? FOR UPDATE",session,claim.projectId());
        for(String episode:samples.stream().map(SkillExperienceConsolidationSample::taskEpisodeId).distinct().sorted().toList())
            jdbc.queryForList("SELECT episode_id FROM ai_ops_task_episode WHERE episode_id=? AND project_id=? FOR UPDATE",episode,claim.projectId());
        jdbc.queryForList("SELECT job_id FROM ai_ops_skill_evolution_job WHERE job_id=? FOR UPDATE",claim.jobId());
        var source=new JdbcSkillEvolutionSourceReader(jdbc);
        if(liveClaim) source.requireClaim(claim);
        var independent=new JdbcVerifiedSkillSourceReader(jdbc).sources(claim.projectId(),claim.agentId(),text(input.get("evolutionClusterKey")));
        Set<String> ids=new HashSet<>();independent.forEach(r->ids.add(text(r.get("acceptance_id"))));
        var primaryIds=JdbcSkillEvolutionSourcePortfolio.primaryIds(input);
        new SkillEvolutionSourceSetPolicy().requireUsable(samples.stream().filter(s->primaryIds.contains(s.sourceId())).toList(),claim.projectId(),claim.runId(),sourceHash);
        if(input.containsKey("sourcePortfolioPolicyVersion")) new JdbcSkillEvolutionSourcePortfolio(jdbc).requireCurrent(claim.projectId(),input);
        for(var sample:samples) {
            if(primaryIds.contains(sample.sourceId()) && !ids.contains(sample.sourceId())) throw new IllegalStateException("SKILL_EVOLUTION_SOURCE_SET_INVALID");
            var current=source.loadAccepted(claim.projectId(),sample.runId(),sample.sourceId());
            if(!sample.sourceHash().equals(current.sourceHash())) throw new IllegalStateException("SKILL_EVOLUTION_PROPOSAL_SOURCE_CHANGED");
        }
        if(baseline) new JdbcSkillEvolutionRelatedSkillGuard(jdbc).lockAndVerify(claim.projectId(),input);
    }

    private List<SkillExperienceConsolidationSample> samples(Map<String,Object> input) {
        return maps(input.get("consolidatedExperiences")).stream().map(s->new SkillExperienceConsolidationSample(
                text(s.get("observationId")),text(s.get("runId")),text(s.get("sessionId")),text(s.get("observationType")),text(s.get("outcome")),
                "","","",0,List.of(),text(s.get("sourceId")),text(s.get("sourceHash")),text(s.get("acceptedTaskEpisode")),
                text(s.get("taskEpisodeId")),text(s.get("conditionKey")))).toList();
    }
    private void requireClaimIdentity(SkillEvolutionJobSnapshot claim) {
        if(claim==null || claim.sourceId().isBlank() || claim.leaseToken().isBlank()) throw new IllegalStateException("SKILL_EVOLUTION_ACCEPTED_CLAIM_REQUIRED");
    }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> maps(Object value) {
        if(!(value instanceof List<?> list)) throw new IllegalStateException("SKILL_EVOLUTION_SOURCE_SET_INVALID");
        return (List<Map<String,Object>>)(List<?>)list;
    }
}
