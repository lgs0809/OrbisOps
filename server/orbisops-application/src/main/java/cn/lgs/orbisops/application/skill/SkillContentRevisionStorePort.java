package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidate;
import java.util.*;

/** Immutable revision receipts and a short fenced model lease, on the same unpublished proposal. */
public interface SkillContentRevisionStorePort {
    record Claim(String candidateId,String candidateHash,String planId,String planHash,int round,String token,
                 Map<String,Object> input,Map<String,Object> previous,List<String> failures,
                 List<Map<String,Object>> batchReviews) { }
    Optional<Claim> claim(SkillPatchCandidate candidate,Map<String,Object> input,List<String> failures);
    boolean complete(Claim claim,Map<String,Object> authored);
    void defer(Claim claim);
    List<Map<String,Object>> history(String candidateId);
}
