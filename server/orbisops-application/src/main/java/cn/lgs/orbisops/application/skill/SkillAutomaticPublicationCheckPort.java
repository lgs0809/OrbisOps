package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidate;
import cn.lgs.orbisops.domain.skill.model.SkillReleaseSnapshot;
import java.util.Map;

/** Revalidates accepted sources and dependencies; called again in the publication transaction. */
public interface SkillAutomaticPublicationCheckPort {
    SkillPublicationSources requireCurrentSources(SkillPatchCandidate candidate);
    boolean runtimeReady(SkillReleaseSnapshot release);
}
