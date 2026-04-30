package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobSnapshot;
import java.util.Map;

/** Durable input and author result on the existing Evolution job, before candidate publication. */
public interface SkillEvolutionProposalPort {
    default java.util.Optional<SkillEvolutionProposalSnapshot> existing(
            cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobSnapshot claim, String sourceHash) {
        return java.util.Optional.empty();
    }
    /** Archive only an unpublished proposal whose baseline is actually stale; keep all evidence. */
    default boolean supersedeChangedBaseline(SkillEvolutionJobSnapshot claim,String sourceHash) { return false; }
    SkillEvolutionProposalSnapshot freeze(SkillEvolutionJobSnapshot claim,String sourceHash,String clusterKey,Map<String,Object> input);
    SkillEvolutionProposalSnapshot authored(SkillEvolutionJobSnapshot claim,SkillEvolutionProposalSnapshot plan,Map<String,Object> result);
    default java.util.Optional<Map<String,Object>> batchReview(SkillEvolutionJobSnapshot claim,SkillEvolutionProposalSnapshot plan,int index,String inputHash) {throw new UnsupportedOperationException("SKILL_SOURCE_BATCH_STORE_REQUIRED");}
    default Map<String,Object> saveBatchReview(SkillEvolutionJobSnapshot claim,SkillEvolutionProposalSnapshot plan,int index,String inputHash,Map<String,Object> review) {throw new UnsupportedOperationException("SKILL_SOURCE_BATCH_STORE_REQUIRED");}
}
