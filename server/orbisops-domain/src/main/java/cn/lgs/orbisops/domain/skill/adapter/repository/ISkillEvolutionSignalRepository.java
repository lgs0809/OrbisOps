package cn.lgs.orbisops.domain.skill.adapter.repository;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionHintSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionSignalSnapshot;

import java.util.List;

/** Persistence port for Skill Evolution signals and authoring hints. */
public interface ISkillEvolutionSignalRepository {

    boolean available();

    SkillEvolutionSignalSnapshot saveIdempotent(SkillEvolutionSignalSnapshot signal);

    void saveHintIdempotent(SkillEvolutionHintSnapshot hint);

    List<SkillEvolutionHintSnapshot> findPendingHints(String projectId, int limit);

    boolean markHintConsumed(String hintId);
}
