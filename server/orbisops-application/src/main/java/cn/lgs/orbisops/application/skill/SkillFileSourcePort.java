package cn.lgs.orbisops.application.skill;

import java.util.List;
import java.util.Optional;

/** File-backed Skill source isolated from the external Skill SDK. */
public interface SkillFileSourcePort {

    List<SkillFileDefinition> findAll();

    Optional<SkillFileDefinition> findById(String skillId);
}
