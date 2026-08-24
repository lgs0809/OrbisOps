package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.*;
import cn.lgs.orbisops.domain.skill.model.*;
import cn.lgs.orbisops.domain.skill.service.*;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.util.*;
import static cn.lgs.orbisops.infrastructure.adapter.repository.TaskEpisodeJson.*;

/** Versioned unpublished candidate projection. Model IO is owned by the outer application adapter. */
@Repository
public class JdbcSkillContentRevisionAdapter implements SkillContentRevisionStorePort {
    private final JdbcTemplate jdbc;
    public JdbcSkillContentRevisionAdapter(@Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbc) {this.jdbc=jdbc;}

    @Override @Transactional(transactionManager="mysqlTransactionManager",isolation=Isolation.READ_COMMITTED)
    public Optional<Claim> claim(SkillPatchCandidate expected,Map<String,Object> input,List<String> failures) {
        var candidate=lockCurrent(expected.candidateId(),expected.candidateHash(),hash(CanonicalJson.stringify(input)));
        var proposal=jdbc.queryForMap("SELECT * FROM ai_ops_skill_evolution_proposal WHERE candidate_id=? FOR UPDATE",candidate.candidateId());
        String plan=text(proposal.get("plan_id")),planHash=text(proposal.get("plan_hash"));
        int completed=jdbc.queryForObject("SELECT count(*) FROM ai_ops_skill_content_revision WHERE candidate_id=? AND status IN ('APPLIED','REJECTED')",Integer.class,candidate.candidateId());
        if(completed>=SkillContentRevisionPolicy.MAX_ROUNDS) return Optional.empty();
        int round=completed+1;
        var review=jdbc.queryForList("SELECT result_json FROM ai_ops_skill_patch_validation WHERE candidate_id=? AND validation_type='CONTENT_REVIEW'",candidate.candidateId());
        if(candidate.status()!=SkillPatchCandidateStatus.POLICY_REJECTED || review.size()!=1)
            throw new IllegalStateException("SKILL_CONTENT_REVISION_REJECTION_REQUIRED");
        var reviewRecord=object(review.get(0).get("result_json"));var reviewed=object(reviewRecord.get("review"));
        if(!candidate.candidateHash().equals(reviewRecord.get("candidateHash")) || !"BLOCK".equals(reviewed.get("decision"))
                || !strings(reviewed.get("reasons")).stream().map(r->"POLICY_CONTENT_REVIEW:"+r).toList().equals(failures))
            throw new IllegalStateException("SKILL_CONTENT_REVISION_REJECTION_REQUIRED");
        String reviewJson=CanonicalJson.stringify(Map.of("failures",failures,"contentReview",reviewRecord));
        jdbc.update("""
                INSERT INTO ai_ops_skill_content_revision(candidate_id,revision_round,plan_id,plan_hash,
                  previous_candidate_hash,previous_candidate_json,previous_authored_json,previous_authored_hash,review_json,status)
                VALUES(?,?,?,?,?,?,?,?,?,'PENDING')
                ON DUPLICATE KEY UPDATE candidate_id=VALUES(candidate_id)
                """,candidate.candidateId(),round,plan,planHash,candidate.candidateHash(),
                CanonicalJson.stringify(SkillPatchCandidateView.of(candidate)),proposal.get("authored_json"),proposal.get("authored_hash"),reviewJson);
        var row=jdbc.queryForMap("SELECT *,lease_until>CURRENT_TIMESTAMP(3) AS live FROM ai_ops_skill_content_revision WHERE candidate_id=? AND revision_round=? FOR UPDATE",candidate.candidateId(),round);
        if(!candidate.candidateHash().equals(row.get("previous_candidate_hash")) || !planHash.equals(row.get("plan_hash")))
            throw deferred("SKILL_CONTENT_REVISION_SOURCE_CHANGED");
        if("LEASED".equals(row.get("status")) && row.get("live")!=null && ((Number)row.get("live")).intValue()!=0)
            throw deferred("SKILL_CONTENT_REVISION_IN_PROGRESS");
        String token=UUID.randomUUID().toString();
        jdbc.update("UPDATE ai_ops_skill_content_revision SET status='LEASED',lease_token=?,lease_until=DATE_ADD(CURRENT_TIMESTAMP(3),INTERVAL 90 SECOND) WHERE candidate_id=? AND revision_round=?",token,candidate.candidateId(),round);
        var frozen=object(proposal.get("input_json"));var pages=new SkillSourceBatchPolicy().pages(frozen);
        var reviews=new ArrayList<Map<String,Object>>();
        if(pages.size()>1) for(int i=0;i<pages.size();i++) reviews.add(new JdbcSkillSourceBatchReview(jdbc)
                .read(plan,frozen,i,CanonicalObjectHasher.sha256(pages.get(i))).orElseThrow(()->deferred("SKILL_SOURCE_BATCH_INCOMPLETE")));
        return Optional.of(new Claim(candidate.candidateId(),candidate.candidateHash(),plan,planHash,round,token,
                frozen,object(row.get("previous_authored_json")),strings(object(row.get("review_json")).get("failures")),reviews));
    }

    @Override @Transactional(transactionManager="mysqlTransactionManager",isolation=Isolation.READ_COMMITTED)
    public boolean complete(Claim claim,Map<String,Object> authored) {
        if(!hash(CanonicalJson.stringify(claim.input())).equals(claim.planHash()))
            throw deferred("SKILL_CONTENT_REVISION_SOURCE_CHANGED");
        var before=lockCurrent(claim.candidateId(),claim.candidateHash(),claim.planHash());
        requireLease(claim);
        String raw=CanonicalJson.stringify(authored);if(raw.length()>1_000_000) throw deferred("SKILL_AUTHORING_RESULT_TOO_LARGE");
        SkillPatchCandidate revised=null;String reason="";
        try {
            revised=new SkillContentRevisionPolicy().revised(before,claim.input(),authored,claim.round(),claim.planHash());
            new JdbcSkillSourceBatchReview(jdbc).requireComplete(claim.planId(),claim.input(),authored);
        } catch(IllegalArgumentException | IllegalStateException invalid) {reason="POLICY_CONTENT_REVISION_INVALID";}
        if(revised!=null && !reason.isBlank()) revised=null;
        jdbc.update("""
                UPDATE ai_ops_skill_content_revision SET status=?,authored_json=?,authored_hash=?,
                  revised_candidate_hash=?,reason_code=?,lease_token='',lease_until=NULL
                WHERE candidate_id=? AND revision_round=? AND lease_token=?
                """,revised==null?"REJECTED":"APPLIED",raw,hash(raw),revised==null?"":revised.candidateHash(),reason,
                claim.candidateId(),claim.round(),claim.token());
        if(revised==null) return false;
        if(jdbc.update("UPDATE ai_ops_skill_evolution_proposal SET authored_json=?,authored_hash=? WHERE plan_id=? AND plan_hash=? AND candidate_id=? AND authored_hash=?",
                raw,hash(raw),claim.planId(),claim.planHash(),claim.candidateId(),hash(CanonicalJson.stringify(claim.previous())))!=1)
            throw deferred("SKILL_CONTENT_REVISION_CONFLICT");
        if(jdbc.update("""
                UPDATE ai_ops_skill_patch_candidate SET candidate_hash=?,changes_json=?,artifacts_json=?,eval_cases_json=?,
                  status='CANDIDATE',reason_code='CONTENT_REVISION_APPLIED',update_time=CURRENT_TIMESTAMP
                WHERE candidate_id=? AND candidate_hash=?
                """,revised.candidateHash(),CanonicalJson.stringify(revised.changes()),CanonicalJson.stringify(revised.artifacts()),
                CanonicalJson.stringify(revised.evalCases()),claim.candidateId(),claim.candidateHash())!=1)
            throw deferred("SKILL_CONTENT_REVISION_CONFLICT");
        return true;
    }

    @Override public void defer(Claim claim) {
        jdbc.update("UPDATE ai_ops_skill_content_revision SET status='PENDING',lease_token='',lease_until=NULL WHERE candidate_id=? AND revision_round=? AND status='LEASED' AND lease_token=?",
                claim.candidateId(),claim.round(),claim.token());
    }

    @Override public List<Map<String,Object>> history(String candidateId) {
        return jdbc.queryForList("SELECT revision_round,status,review_json,reason_code,created_at,updated_at FROM ai_ops_skill_content_revision WHERE candidate_id=? ORDER BY revision_round",candidateId)
                .stream().map(row->{var view=new LinkedHashMap<String,Object>();
                    view.put("round",number(row.get("revision_round")));view.put("status",text(row.get("status")));
                    view.put("reviewReasons",strings(object(row.get("review_json")).get("failures")));
                    view.put("reasonCode",text(row.get("reason_code")));view.put("updatedAt",String.valueOf(row.get("updated_at")));
                    return (Map<String,Object>)view;}).toList();
    }

    private SkillPatchCandidate lockCurrent(String id,String expectedHash,String expectedInputHash) {
        var candidates=new JdbcSkillPatchCandidateAdapter(jdbc);var candidate=candidates.get(id);
        // Use exactly the same source/episode/job/proposal lock order as ordinary publication.
        var input=new JdbcSkillEvolutionProposalAdapter(jdbc).publicationInput(candidate);
        jdbc.queryForList("SELECT candidate_id FROM ai_ops_skill_patch_candidate WHERE candidate_id=? FOR UPDATE",id);
        candidate=candidates.get(id);
        if(!candidate.candidateHash().equals(expectedHash) || !hash(CanonicalJson.stringify(input)).equals(expectedInputHash))
            throw deferred("SKILL_CONTENT_REVISION_SOURCE_CHANGED");
        if(jdbc.queryForObject("SELECT count(*) FROM ai_ops_skill_release WHERE candidate_id=?",Integer.class,id)!=0
                || jdbc.queryForObject("SELECT count(*) FROM ai_ops_skill_version WHERE evolution_job_id=?",Integer.class,id)!=0
                || Set.of("ACTIVE","ROLLED_BACK","DISABLED").contains(candidate.status().name()))
            throw new IllegalStateException("SKILL_CONTENT_REVISION_ALREADY_PUBLISHED");
        return candidate;
    }
    private void requireLease(Claim claim) {
        if(jdbc.queryForObject("SELECT count(*) FROM ai_ops_skill_content_revision WHERE candidate_id=? AND revision_round=? AND status='LEASED' AND lease_token=? AND lease_until>CURRENT_TIMESTAMP(3) FOR UPDATE",
                Integer.class,claim.candidateId(),claim.round(),claim.token())!=1) throw deferred("SKILL_CONTENT_REVISION_LEASE_LOST");
    }
    private List<String> strings(Object value) {
        if(!(value instanceof List<?> list) || list.stream().anyMatch(x->!(x instanceof String))) throw new IllegalStateException("SKILL_CONTENT_REVISION_REVIEW_INVALID");
        return list.stream().map(String.class::cast).toList();
    }
    private SkillContentReviewUnavailableException deferred(String code) {return new SkillContentReviewUnavailableException(new IllegalStateException(code));}
}
