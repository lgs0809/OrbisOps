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

    /** Body-free metadata; runtime may still need an explicitly bound replaced method. */
    default List<SkillCatalogEntry> findEvolutionMetadata(String scope, String projectId) {
        throw new IllegalStateException("SKILL_EVOLUTION_METADATA_STORE_REQUIRED");
    }

    /** New authoring references exclude replaced or rolled-back branches; historical/bound reads remain separate. */
    default List<SkillCatalogEntry> findAuthoringMetadata(String scope,String projectId) {
        return findEvolutionMetadata(scope,projectId);
    }

    Optional<SkillCatalogEntry> find(String scope, String projectId, String skillId, boolean includeContent);

    boolean insertIfAbsent(SkillCatalogEntry entry);

    boolean compareAndSetMutation(SkillCatalogMutation mutation);

    boolean compareAndSetEvolution(SkillEvolutionUpdate update);

    boolean compareAndSetCurrent(SkillCurrentPointerUpdate update);

    boolean compareAndSetGovernance(SkillGovernanceUpdate update);
}
