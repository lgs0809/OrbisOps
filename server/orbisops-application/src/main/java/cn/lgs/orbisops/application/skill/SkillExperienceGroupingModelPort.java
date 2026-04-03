package cn.lgs.orbisops.application.skill;
import cn.lgs.orbisops.domain.skill.model.SkillMethodMemory.*;
import java.util.List;
public interface SkillExperienceGroupingModelPort {
    Extraction extract(String acceptedEpisodeJson);
    Decision decide(Fact fact,List<Group> groups);
}
