package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillPackageVersion;
import cn.lgs.orbisops.domain.skill.model.SkillReleaseSnapshot;

/** Current, independently verified baseline safety. Missing evidence never permits restoration. */
public interface SkillRecoverySafetyPort {
    boolean safeToRestore(SkillReleaseSnapshot release, SkillPackageVersion baseline);
}
