package cn.lgs.orbisops.domain.skill.adapter.repository;

import cn.lgs.orbisops.domain.skill.model.SkillArtifact;
import cn.lgs.orbisops.domain.skill.model.SkillPackageKey;
import cn.lgs.orbisops.domain.skill.model.SkillPackageVersion;

import java.util.List;
import java.util.Optional;

public interface ISkillPackageRepository {

    boolean available();

    Optional<SkillPackageVersion> findVersion(SkillPackageKey key);

    List<SkillPackageVersion> findVersions(String scope, String projectId, String skillId);

    Optional<SkillArtifact> findArtifact(SkillPackageKey key, String artifactPath);

    List<SkillArtifact> findArtifacts(SkillPackageKey key);

    void appendVersion(SkillPackageVersion version, List<SkillArtifact> artifacts);
}
