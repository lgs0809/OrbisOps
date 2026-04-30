package cn.lgs.orbisops.trigger.ops.skill;

import org.springaicommunity.agent.tools.SkillsTool;

import java.util.List;
import java.util.Optional;

/** Minimal read-only Skill catalog surface. */
public interface SkillCatalogReader {

    List<SkillsTool.Skill> loadSkills();

    Optional<SkillsTool.Skill> findSkill(String name);

    List<OpsSkillToolProvider.SkillSummary> listSkillSummaries();

    String toMarkdown(SkillsTool.Skill skill);
}
