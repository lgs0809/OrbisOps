package cn.lgs.orbisops.domain.skill.adapter.repository;

import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;
import cn.lgs.orbisops.domain.skill.model.SkillCatalogMutation;
import cn.lgs.orbisops.domain.skill.model.SkillCurrentPointerUpdate;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionUpdate;
import cn.lgs.orbisops.domain.skill.model.SkillGovernanceUpdate;

import java.util.List;
import java.util.Optional;

public interface ISkillCatalogRepository {

    boolean available();

    List<SkillCatalogEntry> findAll(String scope, String projectId, boolean includeContent);

    Optional<SkillCatalogEntry> find(String scope, String projectId, String skillId, boolean includeContent);

    boolean insertIfAbsent(SkillCatalogEntry entry);

    boolean compareAndSetMutation(SkillCatalogMutation mutation);

    boolean compareAndSetEvolution(SkillEvolutionUpdate update);

    boolean compareAndSetCurrent(SkillCurrentPointerUpdate update);

    boolean compareAndSetGovernance(SkillGovernanceUpdate update);
}
