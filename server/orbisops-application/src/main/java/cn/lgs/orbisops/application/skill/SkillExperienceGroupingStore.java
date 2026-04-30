package cn.lgs.orbisops.application.skill;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillMethodMemory.*;
import java.util.*;
public interface SkillExperienceGroupingStore {
    List<cn.lgs.orbisops.domain.skill.model.SkillEvolutionRunCandidate> ungrouped(int limit);
    Optional<Fact> fact(SkillEvolutionJobSnapshot claim,String sourceHash);
    Fact saveFact(SkillEvolutionJobSnapshot claim,String sourceHash,Extraction extraction);
    Optional<Group> assigned(SkillEvolutionJobSnapshot claim,String sourceHash);
    Optional<Group> current(String projectId,String groupId);
    Group commit(SkillEvolutionJobSnapshot claim,String sourceHash,SkillExperienceRecordResult observation,Decision decision,List<Group> compared);
    void defer(SkillEvolutionJobSnapshot claim,String sourceHash,Decision decision);
}
