package cn.lgs.orbisops.application.skill;
import cn.lgs.orbisops.domain.skill.model.SkillMethodMemory.*;
import java.util.List;
import java.util.Optional;
public interface SkillExperienceGroupingIndexPort {
    Optional<float[]> factEmbedding(String projectId,Fact fact,String modelIdentity);
    void cacheFactEmbedding(String projectId,Fact fact,String modelIdentity,float[] embedding);
    boolean contains(Group group,String modelIdentity);
    void put(Group group,String modelIdentity,float[] embedding);
    List<Ref> search(String projectId,String text,String modelIdentity,float[] query);
}
