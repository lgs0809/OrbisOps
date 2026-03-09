package cn.lgs.orbisops.trigger.ops.skill;

import org.springaicommunity.agent.tools.SkillsTool;

import java.io.IOException;
import java.util.Optional;

/** Minimal editable Skill workspace surface for management commands. */
public interface SkillCatalogEditor {

    Optional<SkillsTool.Skill> findSkill(String name);

    SkillsTool.Skill saveSkill(String name, String markdown) throws IOException;

    void deleteSkill(String name) throws IOException;

    String toMarkdown(SkillsTool.Skill skill);
}
